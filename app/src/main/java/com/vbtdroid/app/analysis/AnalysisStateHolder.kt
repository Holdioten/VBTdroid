package com.vbtdroid.app.analysis

/** Immutable snapshot of the analysis state owned by the foreground service. */
data class AnalysisState(
    val running: Boolean = false,
    val progress: Float = 0f,
    val result: AnalysisResult? = null,
    val error: String? = null,
)

/**
 * Latest analysis state kept by [AnalysisForegroundService] so an Activity/Compose tree that
 * reconnects can restore running progress, a finished result, or an error instead of starting a
 * second analysis. Android-free to keep the consume semantics unit-testable.
 */
class AnalysisStateHolder {
    private var state = AnalysisState()

    @Synchronized
    fun snapshot(): AnalysisState = state

    /** Begins a new run, dropping any previous result/error/progress. */
    @Synchronized
    fun start() {
        state = AnalysisState(running = true, progress = 0f)
    }

    /**
     * Records progress only while running; posted updates that arrive after completion or
     * cancellation are ignored so they cannot resurrect a finished run.
     */
    @Synchronized
    fun updateProgress(value: Float) {
        if (!state.running) return
        state = state.copy(progress = value.coerceIn(0f, 1f))
    }

    @Synchronized
    fun succeed(result: AnalysisResult) {
        state = state.copy(running = false, progress = 1f, result = result, error = null)
    }

    /** Keeps the last progress so the UI can still show how far the failed run got. */
    @Synchronized
    fun fail(message: String) {
        state = state.copy(running = false, error = message, result = null)
    }

    /** User cancellation returns to the initial idle state. */
    @Synchronized
    fun cancel() {
        state = AnalysisState()
    }

    /** Returns the pending result once and clears it so it is never reported twice. */
    @Synchronized
    fun consumeResult(): AnalysisResult? {
        val result = state.result ?: return null
        state = state.copy(result = null)
        return result
    }

    /** Returns the pending error once and clears it. */
    @Synchronized
    fun consumeError(): String? {
        val error = state.error ?: return null
        state = state.copy(error = null)
        return error
    }
}
