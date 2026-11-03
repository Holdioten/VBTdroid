package com.vbtdroid.app.analysis

import kotlinx.coroutines.Job

/**
 * Tracks the single analysis job owned by the foreground service. Re-entry is rejected
 * explicitly so callers can show a state instead of throwing from the service.
 */
internal class AnalysisRunGate {
    private var job: Job? = null

    fun isRunning(): Boolean = job?.isActive == true

    /** Creates and records a job only when no active job exists. */
    fun tryStart(factory: () -> Job): Job? {
        if (isRunning()) return null
        val created = factory()
        job = created
        return created
    }

    /** Clears the slot only when the completing job is still the current one. */
    fun clear(candidate: Job) {
        if (job === candidate) job = null
    }

    fun cancel() {
        job?.cancel()
    }
}
