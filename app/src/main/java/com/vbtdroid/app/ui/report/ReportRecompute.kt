package com.vbtdroid.app.ui.report

import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.analysis.summarize
import com.vbtdroid.app.analysis.withFullSetVelocityLoss
import com.vbtdroid.core.metrics.RepMetricsCalculator
import com.vbtdroid.core.phases.RepSlice

/** Recomputes the edited rep and refreshes full-set-best velocity loss for every affected rep. */
internal fun recomputeManualRep(result: AnalysisResult, updated: RepSlice): AnalysisResult? {
    val recomputed = try {
        RepMetricsCalculator.compute(
            rep = updated,
            samples = result.motionSamples,
            loadKg = result.loadKg,
            peakSamples = result.peakMotionSamples.ifEmpty { result.motionSamples },
        )
    } catch (_: IllegalArgumentException) {
        return null
    }
    val updatedSlices = result.repSlices.map { if (it.index == updated.index) updated else it }
    val reps = withFullSetVelocityLoss(
        result.reps.map { if (it.repIndex == updated.index) recomputed else it },
    )
    val warnings = result.repWarnings.toMutableMap().apply {
        this[updated.index] = (this[updated.index].orEmpty() + "MANUAL_PHASES").distinct()
    }
    return result.copy(
        reps = reps,
        summary = summarize(reps, result.loadKg),
        repSlices = updatedSlices,
        repWarnings = warnings,
    )
}

/** Commits a transient phase edit only when the parent accepts it. */
internal fun resolveManualRepEdit(
    candidate: RepSlice,
    lastValid: RepSlice,
    onEdit: (RepSlice) -> Boolean,
): RepSlice = if (onEdit(candidate)) candidate else lastValid
