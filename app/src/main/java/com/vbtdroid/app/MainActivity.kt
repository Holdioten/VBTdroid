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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.vbtdroid.app.ui.theme.ThemePreference
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.app.ui.theme.VbtThemeId
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
            var themeId by rememberSaveable { mutableStateOf(ThemePreference.load(this).name) }
            val currentTheme = VbtThemeId.valueOf(themeId)
            VbtTheme(currentTheme) {
                AnalysisApp(
                    session = session,
                    themeId = currentTheme,
                    onThemeChange = { id ->
                        themeId = id.name
                        ThemePreference.save(this, id)
                    },
                )
            }
        }
    }
}

private enum class AppScreen { IMPORT, REPORT, HISTORY, LVP }

@Composable
private fun AnalysisApp(
    session: AnalysisSession,
    themeId: VbtThemeId,
    onThemeChange: (VbtThemeId) -> Unit,
) {
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
    var oneRmEstimates by remember { mutableStateOf<List<com.vbtdroid.app.data.OneRmEstimateEntity>>(emptyList()) }
    var historyDetail by remember { mutableStateOf<HistoryDetail?>(null) }
    val serviceUnavailable = stringResource(R.string.analysis_service_unavailable)

    fun callbacksFor(): AnalysisForegroundService.Callbacks =
        object : AnalysisForegroundService.Callbacks {
            override fun onProgress(value: Float) {
                if (analyzing) progress = value
            }

            override fun onResult(value: AnalysisResult) {
                // Keep the service-owned copy: this composition may already be detached (the
                // Activity can be destroyed while backgrounded), and only onResume/onServiceConnected
                // can decide whether the result still needs showing. syncWithService() consumes it.
                analyzing = false
                progress = 1f
                analysisError = null
                publishResult(value)
                screen = AppScreen.REPORT
            }

            override fun onError(message: String) {
                // Same reasoning as onResult: the stored error is consumed by syncWithService().
                analyzing = false
                analysisError = message
            }
        }

    /** Adopts the service-owned state after connecting or returning to the foreground. */
    fun syncWithService(binder: AnalysisForegroundService.AnalysisBinder) {
        val state = binder.state()
        if (state.running) {
            // Never start a second analysis: show the running one and follow it again.
            analyzing = true
            progress = state.progress
            analysisError = null
            binder.attach(callbacksFor())
            screen = AppScreen.IMPORT
            return
        }
        analyzing = false
        val completed = binder.consumeResult()
        if (completed != null) {
            progress = 1f
            analysisError = null
            // A live composition may already show this report; only navigate for a new result.
            if (resultState !== completed) {
                publishResult(completed)
                screen = AppScreen.REPORT
            }
            return
        }
        binder.consumeError()?.let { analysisError = it }
    }

    val connection = remember(context) {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val analysisBinder = binder as AnalysisForegroundService.AnalysisBinder
                serviceBinder = analysisBinder
                syncWithService(analysisBinder)
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

    // Returning from background does not recreate the composition, so re-read the service state.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, connection) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceBinder?.let { syncWithService(it) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openHistory() {
        scope.launch {
            history = repository.history()
            oneRmEstimates = repository.oneRmEstimates()
            historyDetail = null
            screen = AppScreen.HISTORY
        }
    }

    // Restored into HISTORY after recreation: the in-memory list is gone, so reload it.
    LaunchedEffect(Unit) {
        if (screen == AppScreen.HISTORY || screen == AppScreen.LVP) {
            history = repository.history()
            oneRmEstimates = repository.oneRmEstimates()
        }
    }

    fun backFromReport() {
        // The report was explicitly dismissed; drop the service copy so returning to the app
        // cannot reopen it.
        serviceBinder?.consumeResult()
        publishResult(null)
        analysisError = null
        progress = 0f
        screen = AppScreen.IMPORT
    }

    // System Back never finishes the app from a sub-screen: REPORT/HISTORY return to IMPORT.
    BackHandler(enabled = screen != AppScreen.IMPORT) {
        when (screen) {
            AppScreen.HISTORY -> screen = if (resultState == null) AppScreen.IMPORT else AppScreen.REPORT
            AppScreen.LVP -> screen = AppScreen.HISTORY
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
            estimates = oneRmEstimates,
            detail = historyDetail,
            onSelect = { setId ->
                scope.launch { historyDetail = repository.detail(setId) }
            },
            onCloseDetail = { historyDetail = null },
            onBack = { screen = if (resultState == null) AppScreen.IMPORT else AppScreen.REPORT },
            onEstimate1Rm = { screen = AppScreen.LVP },
        )
        AppScreen.LVP -> com.vbtdroid.app.ui.lvp.LvpEstimatorScreen(
            history = history,
            estimates = oneRmEstimates,
            onSave = { estimate ->
                scope.launch {
                    repository.saveOneRmEstimate(estimate)
                    oneRmEstimates = repository.oneRmEstimates()
                }
            },
            onBack = { screen = AppScreen.HISTORY },
        )
        AppScreen.IMPORT -> ImportScreen(
            analyzing = analyzing,
            analysisProgress = progress,
            analysisError = analysisError,
            onAnalyze = { request ->
                scope.launch {
                    val cached = repository.setByAnalysisKey(request.analysisKey)
                    if (cached != null) {
                        historyDetail = repository.detail(cached.setId)
                        screen = AppScreen.HISTORY
                        android.widget.Toast.makeText(
                            context,
                            R.string.import_reused_analysis,
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        val binder = serviceBinder
                        if (binder == null) {
                            analysisError = serviceUnavailable
                        } else {
                            analyzing = true
                            analysisError = null
                            progress = 0f
                            context.startForegroundService(
                                Intent(context, AnalysisForegroundService::class.java),
                            )
                            val startResult = binder.analyze(
                                request = request,
                                callbacks = callbacksFor(),
                            )
                            if (startResult == AnalysisStartResult.ALREADY_RUNNING) {
                                // A run survived while this Activity was away: adopt it, do not restart.
                                syncWithService(binder)
                            }
                        }
                    }
                }
            },
            onCancel = {
                serviceBinder?.cancel()
                analyzing = false
                analysisError = null
            },
            onShowHistory = ::openHistory,
            suggestRois = { bitmap -> serviceBinder?.suggestRois(bitmap).orEmpty() },
            themeId = themeId,
            onThemeChange = onThemeChange,
        )
    }
}
