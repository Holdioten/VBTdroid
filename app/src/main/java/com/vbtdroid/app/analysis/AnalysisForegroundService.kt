package com.vbtdroid.app.analysis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vbtdroid.app.MainActivity
import com.vbtdroid.app.R
import com.vbtdroid.app.data.AnalysisRepository
import com.vbtdroid.app.data.AppDatabase
import com.vbtdroid.app.overlay.VideoOverlayExporter
import com.vbtdroid.app.video.VideoDecoder
import com.vbtdroid.app.vision.MediaPipePoseEstimator
import com.vbtdroid.app.vision.PlateDetector
import com.vbtdroid.app.vision.PlateTracker
import com.vbtdroid.app.vision.PoseEstimator
import com.vbtdroid.app.vision.PoseFrame
import com.vbtdroid.app.vision.TflitePlateDetector
import com.vbtdroid.app.vision.YoloPlateDetector
import com.vbtdroid.core.metrics.Point2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Result of trying to start an analysis; re-entry is reported, never thrown. */
enum class AnalysisStartResult { STARTED, ALREADY_RUNNING }

/** Owns long analysis independently of Compose/Activity lifetime. */
class AnalysisForegroundService : Service() {
    interface Callbacks {
        fun onProgress(progress: Float)
        fun onResult(result: AnalysisResult)
        fun onError(message: String)
    }

    inner class AnalysisBinder : Binder() {
        fun analyze(request: AnalysisRequest, callbacks: Callbacks): AnalysisStartResult =
            startAnalysis(request, callbacks)

        /** Replaces the delivery target without disturbing a run already in flight. */
        fun attach(callbacks: Callbacks) {
            this@AnalysisForegroundService.callbacks = callbacks
        }

        fun state(): AnalysisState = stateHolder.snapshot()
        fun consumeResult(): AnalysisResult? = stateHolder.consumeResult()
        fun consumeError(): String? = stateHolder.consumeError()
        fun cancel() = cancelAnalysis()
        fun isRunning(): Boolean = runGate.isRunning()
        fun suggestRoi(bitmap: Bitmap): RectF? = PlateTracker(plateDetector).use { it.initialRoi(bitmap) }
    }

    private val binder = AnalysisBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val runGate = AnalysisRunGate()
    private val stateHolder = AnalysisStateHolder()

    /** Latest callbacks from whichever Activity/Compose tree is currently attached. */
    @Volatile
    private var callbacks: Callbacks? = null

    private val decoder by lazy { VideoDecoder(applicationContext) }
    private val databaseDelegate = lazy { AppDatabase.build(applicationContext) }
    private val database by databaseDelegate
    private val repository by lazy { AnalysisRepository(database) }
    private val plateDetectorDelegate = lazy<PlateDetector?> {
        runCatching<PlateDetector> { YoloPlateDetector(applicationContext) }
            .recoverCatching { TflitePlateDetector(applicationContext) }
            .getOrNull()
    }
    private val plateDetector by plateDetectorDelegate
    private val mediaPipePoseDelegate = lazy { runCatching { MediaPipePoseEstimator(applicationContext) }.getOrNull() }
    private val mediaPipePose by mediaPipePoseDelegate
    private val poseEstimator: PoseEstimator by lazy {
        DeferredPoseEstimator { mediaPipePose ?: NoPoseEstimator }
    }
    private val plateTracker by lazy { PlateTracker(plateDetector) }
    private val pipelineDelegate = lazy { AnalysisPipeline(decoder, plateTracker, poseEstimator) }
    private val pipeline by pipelineDelegate
    private val overlayExporter by lazy { VideoOverlayExporter() }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() requires a prompt startForeground() call; analysis then
        // replaces no state and simply reuses the same notification.
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, analysisNotification())
        return START_NOT_STICKY
    }

    private fun startAnalysis(request: AnalysisRequest, callbacks: Callbacks): AnalysisStartResult {
        // A reconnecting Activity gets the current state even when a run is already active; it
        // must never start a duplicate analysis.
        this.callbacks = callbacks
        lateinit var job: Job
        val started = runGate.tryStart {
            stateHolder.start()
            serviceScope.launch(start = CoroutineStart.LAZY) {
                try {
                    val analyzed = pipeline.analyze(request) { progress ->
                        deliverProgress(AnalysisOverlayJob.analysisProgress(progress))
                    }
                    if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                        writeDebugDump(analyzed)
                    }
                    // One background job owns both passes: analysis 0..0.75, overlay 0.75..1.0.
                    deliverProgress(AnalysisOverlayJob.ANALYSIS_SHARE)
                    val withOverlay = attachOverlay(request, analyzed)
                    val saved = repository.saveAnalysis(withOverlay, request)
                    deliverProgress(1f)
                    withContext(Dispatchers.Main.immediate) {
                        stateHolder.succeed(saved)
                        callbacksForDelivery()?.onResult(saved)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    val message = error.message ?: error::class.java.simpleName
                    withContext(Dispatchers.Main.immediate) {
                        stateHolder.fail(message)
                        callbacksForDelivery()?.onError(message)
                    }
                } finally {
                    runGate.clear(job)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        } ?: return AnalysisStartResult.ALREADY_RUNNING

        job = started
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, analysisNotification())
        job.start()
        return AnalysisStartResult.STARTED
    }

    /** Posts progress to the main thread and records it for later reattachment. */
    private fun deliverProgress(value: Float) {
        mainHandler.post {
            stateHolder.updateProgress(value)
            callbacksForDelivery()?.onProgress(value)
        }
    }

    private fun callbacksForDelivery(): Callbacks? = callbacks

    /**
     * Renders the overlay MP4 into app cache and returns the result carrying its path. A failed or
     * cancelled export never fails the analysis: the failure is turned into a quality warning and
     * the partial file is removed.
     */
    private suspend fun attachOverlay(
        request: AnalysisRequest,
        analyzed: AnalysisResult,
    ): AnalysisResult {
        val output = File(cacheDir, AnalysisOverlayJob.overlayFileName(analyzed.createdAtEpochMs))
        try {
            overlayExporter.exportToFile(
                context = applicationContext,
                sourceUri = request.uri,
                outputFile = output,
                result = analyzed,
            ) { progress ->
                deliverProgress(AnalysisOverlayJob.overlayProgress(progress))
            }
            return analyzed.copy(overlayVideoPath = output.absolutePath)
        } catch (cancelled: CancellationException) {
            output.delete()
            throw cancelled
        } catch (error: Exception) {
            output.delete()
            Log.w(TAG, "Overlay export failed; delivering analysis without video", error)
            return analyzed.copy(
                overlayVideoPath = null,
                quality = analyzed.quality.copy(
                    warnings = (analyzed.quality.warnings + AnalysisOverlayJob.OVERLAY_FAILED_WARNING)
                        .distinct(),
                ),
            )
        } finally {
            cleanupOverlayCache(current = output)
        }
    }

    /** Keeps the newest few overlay MP4s so repeated analyses cannot grow cache without bound. */
    private fun cleanupOverlayCache(current: File? = null) {
        val files = cacheDir.listFiles { file ->
            file.isFile && file.name.startsWith("overlay-") && file.name.endsWith(".mp4")
        }?.sortedByDescending { it.lastModified() } ?: return
        AnalysisOverlayJob.staleOverlayFiles(files.map { it.name })
            .filterNot { it == current?.name }
            .forEach { name ->
                runCatching { File(cacheDir, name).delete() }
            }
    }

    /** Debug-only trace so a device run can be replayed locally without guessing. */
    private fun writeDebugDump(result: AnalysisResult) {
        runCatching {
            File(cacheDir, "debug-motion.csv").printWriter().use { writer ->
                writer.println("t,y_m,vy_mps")
                result.motionSamples.forEach { sample ->
                    writer.println("${sample.tSec},${sample.yM},${sample.vyMps}")
                }
            }
            File(cacheDir, "debug-observations.csv").printWriter().use { writer ->
                writer.println("t,x_px,y_px,diameter_px,confidence")
                result.observations.forEach { observation ->
                    writer.println(
                        "${observation.tSec},${observation.xPx},${observation.yPx}," +
                            "${observation.majorAxisPx},${observation.confidence}",
                    )
                }
            }
            File(cacheDir, "debug-reps.txt").writeText(
                buildString {
                    appendLine("reps=${result.reps.size} trackLostFromSec=${result.trackLostFromSec}")
                    appendLine("warnings=${result.quality.warnings}")
                    appendLine("poseFrames=${result.poseFrames.size} angles=${result.frameAngles.size}")
                    result.repSlices.forEach { slice ->
                        appendLine("${slice.index}: ${slice.startSec}..${slice.endSec} ${slice.phases}")
                    }
                },
            )
        }
    }

    private fun cancelAnalysis() {
        stateHolder.cancel()
        runGate.cancel()
    }

    override fun onDestroy() {
        // Do not reset the holder here: destruction after a finished run must not wipe a result
        // that no Activity has consumed yet. User cancellation calls cancelAnalysis().
        runGate.cancel()
        serviceScope.cancel()
        if (pipelineDelegate.isInitialized()) plateTracker.close()
        if (plateDetectorDelegate.isInitialized()) plateDetector?.close()
        if (mediaPipePoseDelegate.isInitialized()) mediaPipePose?.close()
        if (databaseDelegate.isInitialized()) database.close()
        super.onDestroy()
    }

    private fun analysisNotification(): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.analysis_notification_title))
            .setContentText(getString(R.string.analysis_notification_text))
            .setContentIntent(launch)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.analysis_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private object NoPoseEstimator : PoseEstimator {
        override fun estimate(bitmap: Bitmap, tSec: Double, barCenter: Point2?): PoseFrame? = null
    }

    private companion object {
        const val CHANNEL_ID = "offline_analysis"
        const val NOTIFICATION_ID = 41
        const val TAG = "AnalysisService"
    }
}
