package com.vbtdroid.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vbtdroid.app.analysis.AnalysisForegroundService
import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.analysis.AnalysisStartResult
import com.vbtdroid.app.data.AnalysisRepository
import com.vbtdroid.app.data.AppDatabase
import com.vbtdroid.app.data.HistoryDetail
import com.vbtdroid.app.data.SetEntity
import com.vbtdroid.app.ui.history.HistoryScreen
import com.vbtdroid.app.ui.import.ImportScreen
import com.vbtdroid.app.ui.report.ReportScreen
import com.vbtdroid.app.ui.theme.VbtTheme
import kotlinx.coroutines.launch

/**
 * Holds the last analysis result across Activity recreation (rotation, app switch while the
 * Activity is recreated). Process death clears it; the restored screen then falls back to import.
 */
class AnalysisSession : ViewModel() {
    var result: AnalysisResult? = null
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 42)
        }
        val session = ViewModelProvider(this)[AnalysisSession::class.java]
        setContent {
            VbtTheme {
                AnalysisApp(session)
            }
        }
    }
}

private enum class AppScreen { IMPORT, REPORT, HISTORY }

@Composable
private fun AnalysisApp(session: AnalysisSession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember(context) { AppDatabase.build(context) }
    val repository = remember(database) { AnalysisRepository(database) }
    var serviceBinder by remember { mutableStateOf<AnalysisForegroundService.AnalysisBinder?>(null) }
    var screen by rememberSaveable { mutableStateOf(AppScreen.IMPORT) }
    var resultState by remember { mutableStateOf(session.result) }
    fun publishResult(value: AnalysisResult?) {
        session.result = value
        resultState = value
    }
    var analyzing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf<List<SetEntity>>(emptyList()) }
    var historyDetail by remember { mutableStateOf<HistoryDetail?>(null) }
    val serviceUnavailable = stringResource(R.string.analysis_service_unavailable)
    val analysisAlreadyRunning = stringResource(R.string.analysis_already_running)

    val connection = remember(context) {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                serviceBinder = binder as AnalysisForegroundService.AnalysisBinder
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceBinder = null
                if (analyzing) {
                    analyzing = false
                    analysisError = serviceUnavailable
                }
            }
        }
    }
    DisposableEffect(context, database) {
        context.bindService(
            Intent(context, AnalysisForegroundService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        onDispose {
            context.unbindService(connection)
            database.close()
        }
    }

    fun openHistory() {
        scope.launch {
            history = repository.history()
            historyDetail = null
            screen = AppScreen.HISTORY
        }
    }

    // Restored into HISTORY after recreation: the in-memory list is gone, so reload it.
    LaunchedEffect(Unit) {
        if (screen == AppScreen.HISTORY) history = repository.history()
    }

    fun backFromReport() {
        publishResult(null)
        analysisError = null
        progress = 0f
        screen = AppScreen.IMPORT
    }

    // System Back never finishes the app from a sub-screen: REPORT/HISTORY return to IMPORT.
    BackHandler(enabled = screen != AppScreen.IMPORT) {
        when (screen) {
            AppScreen.HISTORY -> screen = if (resultState == null) AppScreen.IMPORT else AppScreen.REPORT
            AppScreen.REPORT -> backFromReport()
            AppScreen.IMPORT -> Unit
        }
    }

    when (screen) {
        AppScreen.REPORT -> resultState?.let { report ->
            ReportScreen(
                result = report,
                onBack = ::backFromReport,
                onShowHistory = ::openHistory,
                onPersist = { updated -> repository.updateSavedAnalysis(updated) },
            )
        } ?: run { screen = AppScreen.IMPORT }
        AppScreen.HISTORY -> HistoryScreen(
            history = history,
            detail = historyDetail,
            onSelect = { setId ->
                scope.launch { historyDetail = repository.detail(setId) }
            },
            onCloseDetail = { historyDetail = null },
            onBack = { screen = if (resultState == null) AppScreen.IMPORT else AppScreen.REPORT },
        )
        AppScreen.IMPORT -> ImportScreen(
            analyzing = analyzing,
            analysisProgress = progress,
            analysisError = analysisError,
            onAnalyze = { request ->
                val binder = serviceBinder
                if (binder == null) {
                    analysisError = serviceUnavailable
                } else {
                    analyzing = true
                    analysisError = null
                    progress = 0f
                    context.startForegroundService(Intent(context, AnalysisForegroundService::class.java))
                    val startResult = binder.analyze(
                        request = request,
                        callbacks = object : AnalysisForegroundService.Callbacks {
                            override fun onProgress(value: Float) {
                                if (analyzing) progress = value
                            }

                            override fun onResult(value: AnalysisResult) {
                                analyzing = false
                                publishResult(value)
                                screen = AppScreen.REPORT
                            }

                            override fun onError(message: String) {
                                analyzing = false
                                analysisError = message
                            }
                        },
                    )
                    if (startResult == AnalysisStartResult.ALREADY_RUNNING) {
                        analyzing = false
                        analysisError = analysisAlreadyRunning
                    }
                }
            },
            onCancel = {
                serviceBinder?.cancel()
                analyzing = false
                analysisError = null
            },
            onShowHistory = ::openHistory,
            suggestRoi = { bitmap -> serviceBinder?.suggestRoi(bitmap) },
        )
    }
}
