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
import com.vbtdroid.app.ui.import.ImportScreen
import com.vbtdroid.app.ui.report.ReportScreen
import com.vbtdroid.app.video.VideoDecoder
import com.vbtdroid.app.vision.MediaPipePoseEstimator
import com.vbtdroid.app.vision.PlateTracker
import com.vbtdroid.app.vision.PoseEstimator
import com.vbtdroid.app.vision.TflitePlateDetector
import kotlinx.coroutines.CancellationException
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

@Composable
private fun AnalysisApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val decoder = remember(context) { VideoDecoder(context) }
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
    DisposableEffect(plateDetector, mediaPipePose) {
        onDispose {
            plateDetector?.close()
            mediaPipePose?.close()
        }
    }

    var result by remember { mutableStateOf<AnalysisResult?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var failed by remember { mutableStateOf(false) }

    result?.let { report ->
        ReportScreen(
            result = report,
            onBack = {
                result = null
                failed = false
                progress = 0f
            },
        )
    } ?: ImportScreen(
        analyzing = analyzing,
        analysisProgress = progress,
        analysisFailed = failed,
        onAnalyze = { request ->
            scope.launch {
                analyzing = true
                failed = false
                progress = 0f
                try {
                    result = pipeline.analyze(request) { progress = it }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed = true
                } finally {
                    analyzing = false
                }
            }
        },
    )
}

private object NoPoseEstimator : PoseEstimator {
    override fun estimate(bitmap: Bitmap, tSec: Double) = null
}
