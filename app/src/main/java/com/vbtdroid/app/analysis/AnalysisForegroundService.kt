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
import androidx.core.app.NotificationCompat
import com.vbtdroid.app.MainActivity
import com.vbtdroid.app.R
import com.vbtdroid.app.data.AnalysisRepository
import com.vbtdroid.app.data.AppDatabase
import com.vbtdroid.app.video.VideoDecoder
import com.vbtdroid.app.vision.MediaPipePoseEstimator
import com.vbtdroid.app.vision.PlateTracker
import com.vbtdroid.app.vision.PoseEstimator
import com.vbtdroid.app.vision.PoseFrame
import com.vbtdroid.app.vision.TflitePlateDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns long analysis independently of Compose/Activity lifetime. */
class AnalysisForegroundService : Service() {
    interface Callbacks {
        fun onProgress(progress: Float)
        fun onResult(result: AnalysisResult)
        fun onError(message: String)
    }

    inner class AnalysisBinder : Binder() {
        fun analyze(request: AnalysisRequest, callbacks: Callbacks): Job = startAnalysis(request, callbacks)
        fun cancel() = cancelAnalysis()
        fun suggestRoi(bitmap: Bitmap): RectF? = PlateTracker(plateDetector).use { it.initialRoi(bitmap) }
    }

    private val binder = AnalysisBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var analysisJob: Job? = null

    private val decoder by lazy { VideoDecoder(applicationContext) }
    private val databaseDelegate = lazy { AppDatabase.build(applicationContext) }
    private val database by databaseDelegate
    private val repository by lazy { AnalysisRepository(database) }
    private val plateDetectorDelegate = lazy { runCatching { TflitePlateDetector(applicationContext) }.getOrNull() }
    private val plateDetector by plateDetectorDelegate
    private val mediaPipePoseDelegate = lazy { runCatching { MediaPipePoseEstimator(applicationContext) }.getOrNull() }
    private val mediaPipePose by mediaPipePoseDelegate
    private val poseEstimator: PoseEstimator by lazy { mediaPipePose ?: NoPoseEstimator }
    private val plateTracker by lazy { PlateTracker(plateDetector) }
    private val pipelineDelegate = lazy { AnalysisPipeline(decoder, plateTracker, poseEstimator) }
    private val pipeline by pipelineDelegate

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() requires a prompt startForeground() call; analysis then
        // replaces no state and simply reuses the same notification.
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, analysisNotification())
        return START_NOT_STICKY
    }

    private fun startAnalysis(request: AnalysisRequest, callbacks: Callbacks): Job {
        check(analysisJob?.isActive != true) { "Analysis is already running" }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, analysisNotification())
        val job = serviceScope.launch {
            try {
                val analyzed = pipeline.analyze(request) { progress ->
                    mainHandler.post { callbacks.onProgress(progress) }
                }
                val saved = repository.saveAnalysis(analyzed, request)
                withContext(Dispatchers.Main.immediate) { callbacks.onResult(saved) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                withContext(Dispatchers.Main.immediate) {
                    callbacks.onError(error.message ?: error::class.java.simpleName)
                }
            } finally {
                analysisJob = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        analysisJob = job
        return job
    }

    private fun cancelAnalysis() {
        analysisJob?.cancel()
    }

    override fun onDestroy() {
        analysisJob?.cancel()
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
        override fun estimate(bitmap: Bitmap, tSec: Double): PoseFrame? = null
    }

    private companion object {
        const val CHANNEL_ID = "offline_analysis"
        const val NOTIFICATION_ID = 41
    }
}
