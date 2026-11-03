package com.vbtdroid.app.ui.report

import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.analysis.summarize
import com.vbtdroid.core.metrics.RepMetricsCalculator
import com.vbtdroid.core.phases.RepSlice

/**
 * Recomputes one manually edited rep without retroactively changing any other rep.
 * Velocity loss uses only earlier valid reps, matching the pipeline's forward pass.
 */
internal fun recomputeManualRep(result: AnalysisResult, updated: RepSlice): AnalysisResult? {
    val bestPreviousMcv = result.reps
        .filter { it.repIndex < updated.index }
        .maxOfOrNull { it.mcvMps }
    val recomputed = try {
        RepMetricsCalculator.compute(
            rep = updated,
            samples = result.motionSamples,
            loadKg = result.loadKg,
            bestMcvMps = bestPreviousMcv,
        )
    } catch (_: IllegalArgumentException) {
        return null
    }
    val reps = result.reps.map { if (it.repIndex == updated.index) recomputed else it }
    return result.copy(
        reps = reps,
        summary = summarize(reps, result.loadKg),
        repSlices = result.repSlices.map { if (it.index == updated.index) updated else it },
    )
}

/** Commits a transient phase edit only when the parent accepts it. */
internal fun resolveManualRepEdit(
    candidate: RepSlice,
    lastValid: RepSlice,
    onEdit: (RepSlice) -> Boolean,
): RepSlice = if (onEdit(candidate)) candidate else lastValid
