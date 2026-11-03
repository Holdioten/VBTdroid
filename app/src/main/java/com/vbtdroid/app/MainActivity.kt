package com.vbtdroid.app

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vbtdroid.app.analysis.AnalysisPipeline
import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.data.AnalysisRepository
import com.vbtdroid.app.data.AppDatabase
import com.vbtdroid.app.data.HistoryDetail
import com.vbtdroid.app.data.SetEntity
import com.vbtdroid.app.ui.history.HistoryScreen
import com.vbtdroid.app.ui.import.ImportScreen
import com.vbtdroid.app.ui.report.ReportScreen
import com.vbtdroid.app.video.VideoDecoder
import com.vbtdroid.app.vision.MediaPipePoseEstimator
import com.vbtdroid.app.vision.PlateTracker
import com.vbtdroid.app.vision.PoseEstimator
import com.vbtdroid.app.vision.TflitePlateDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AnalysisApp()
            }
        }
    }
}

private enum class AppScreen { IMPORT, REPORT, HISTORY }

@Composable
private fun AnalysisApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val decoder = remember(context) { VideoDecoder(context) }
    val database = remember(context) { AppDatabase.build(context) }
    val repository = remember(database) { AnalysisRepository(database) }
    val plateDetector = remember(context) {
        runCatching { TflitePlateDetector(context) }.getOrNull()
    }
    val mediaPipePose = remember(context) {
        runCatching { MediaPipePoseEstimator(context) }.getOrNull()
    }
    val poseEstimator: PoseEstimator = mediaPipePose ?: NoPoseEstimator
    val pipeline = remember(decoder, plateDetector, poseEstimator) {
        AnalysisPipeline(decoder, PlateTracker(plateDetector), poseEstimator)
    }
    DisposableEffect(plateDetector, mediaPipePose, database) {
        onDispose {
            plateDetector?.close()
            mediaPipePose?.close()
            database.close()
        }
    }

    var screen by remember { mutableStateOf(AppScreen.IMPORT) }
    var result by remember { mutableStateOf<AnalysisResult?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var analysisJob by remember { mutableStateOf<Job?>(null) }
    var history by remember { mutableStateOf<List<SetEntity>>(emptyList()) }
    var historyDetail by remember { mutableStateOf<HistoryDetail?>(null) }

    fun openHistory() {
        scope.launch {
            history = repository.history()
            historyDetail = null
            screen = AppScreen.HISTORY
        }
    }

    when (screen) {
        AppScreen.REPORT -> result?.let { report ->
            ReportScreen(
                result = report,
                onBack = {
                    result = null
                    analysisError = null
                    progress = 0f
                    screen = AppScreen.IMPORT
                },
                onShowHistory = ::openHistory,
            )
        } ?: run { screen = AppScreen.IMPORT }
        AppScreen.HISTORY -> HistoryScreen(
            history = history,
            detail = historyDetail,
            onSelect = { setId ->
                scope.launch { historyDetail = repository.detail(setId) }
            },
            onCloseDetail = { historyDetail = null },
            onBack = { screen = if (result == null) AppScreen.IMPORT else AppScreen.REPORT },
        )
        AppScreen.IMPORT -> ImportScreen(
            analyzing = analyzing,
            analysisProgress = progress,
            analysisError = analysisError,
            onAnalyze = { request ->
                analysisJob = scope.launch {
                    analyzing = true
                    analysisError = null
                    progress = 0f
                    try {
                        val analyzed = pipeline.analyze(request) { progress = it }
                        result = repository.saveAnalysis(analyzed, request)
                        screen = AppScreen.REPORT
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        analysisError = error.message ?: error::class.java.simpleName
                    } finally {
                        analyzing = false
                        analysisJob = null
                    }
                }
            },
            onCancel = {
                analysisJob?.cancel()
                analysisError = null
            },
            onShowHistory = ::openHistory,
            suggestRoi = { bitmap -> PlateTracker(plateDetector).initialRoi(bitmap) },
        )
    }
}

private object NoPoseEstimator : PoseEstimator {
    override fun estimate(bitmap: Bitmap, tSec: Double) = null
}
