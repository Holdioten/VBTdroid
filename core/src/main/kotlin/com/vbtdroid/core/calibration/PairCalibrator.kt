package com.vbtdroid.core.calibration

import kotlin.math.abs

/**
 * Result of calibrating one plate against the other end of the same barbell.
 *
 * A barbell is rigid: at any instant both plates travel the same vertical distance. Their image
 * displacement ratio is therefore the ratio of their true diameters, independent of pixels per
 * metre. Anchoring the ratio to the end whose detection box hugs the rim (usually the far,
 * smaller plate) removes the systematic 5-10% scale bias of the other end without asking the
 * user for anything.
 */
data class PairCalibration(
    val diameterPx: Double,
    /** Primary/secondary travel ratio; null when the pair could not be used. */
    val ratio: Double?,
    val usedPair: Boolean,
    /** Why the pair was not used; null when [usedPair] is true or the primary is the anchor. */
    val fallbackReason: String?,
)

object PairCalibrator {
    const val NO_SECONDARY = "NO_SECONDARY"
    const val LOW_COVERAGE = "LOW_COVERAGE"
    const val LOW_CORRELATION = "LOW_CORRELATION"
    const val FEW_RATIOS = "FEW_RATIOS"
    const val RATIO_OUT_OF_RANGE = "RATIO_OUT_OF_RANGE"
    const val RATIO_UNSTABLE = "RATIO_UNSTABLE"

    /** Two plates on one bar cannot differ by more than this (45 cm vs 15 cm is already 3x). */
    const val MIN_RATIO = 1.2
    const val MAX_RATIO = 3.0

    /** Per-rep ratio spread above this means the tracks do not describe the same motion. */
    const val MAX_IQR_FRACTION = 0.15

    /** Ratios further than this from the median are dropped before the final estimate. */
    const val OUTLIER_FRACTION = 0.10

    const val MIN_RATIOS = 2
    const val MIN_COVERAGE = 0.8
    const val MIN_CORRELATION = 0.95

    fun calibrate(
        primaryBoxPx: Double,
        secondaryBoxPx: Double?,
        ratios: List<Double>,
        coverage: Double,
        correlation: Double,
    ): PairCalibration {
        val primary = primaryBoxPx.takeIf { it.isFinite() && it > 0.0 }
            ?: return PairCalibration(Double.NaN, null, false, NO_SECONDARY)
        val secondary = secondaryBoxPx?.takeIf { it.isFinite() && it > 0.0 }
            ?: return PairCalibration(primary, null, false, NO_SECONDARY)
        if (!coverage.isFinite() || coverage < MIN_COVERAGE) {
            return PairCalibration(primary, null, false, LOW_COVERAGE)
        }
        if (!correlation.isFinite() || correlation < MIN_CORRELATION) {
            return PairCalibration(primary, null, false, LOW_CORRELATION)
        }
        val valid = ratios.filter { it.isFinite() && it > 0.0 }
        if (valid.size < MIN_RATIOS) {
            return PairCalibration(primary, null, false, FEW_RATIOS)
        }
        val median = median(valid)
        if (median < MIN_RATIO || median > MAX_RATIO) {
            return PairCalibration(primary, null, false, RATIO_OUT_OF_RANGE)
        }
        val trimmed = valid.filter { abs(it - median) / median <= OUTLIER_FRACTION }
        if (trimmed.size < MIN_RATIOS) {
            return PairCalibration(primary, null, false, RATIO_UNSTABLE)
        }
        val refined = median(trimmed)
        val q1 = quantile(trimmed, 0.25)
        val q3 = quantile(trimmed, 0.75)
        if ((q3 - q1) / refined > MAX_IQR_FRACTION) {
            return PairCalibration(primary, null, false, RATIO_UNSTABLE)
        }
        // The smaller box is the anchor: it is the end whose box hugs the plate rim, and using it
        // for both the far and the near analysis keeps the two runs on one scale.
        if (primary <= secondary) {
            return PairCalibration(primary, refined, false, null)
        }
        return PairCalibration(secondary * refined, refined, true, null)
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    private fun quantile(values: List<Double>, fraction: Double): Double {
        val sorted = values.sorted()
        if (sorted.isEmpty()) return Double.NaN
        val position = ((sorted.size - 1) * fraction).coerceIn(0.0, (sorted.size - 1).toDouble())
        val lower = position.toInt()
        val upper = (lower + 1).coerceAtMost(sorted.size - 1)
        val weight = position - lower
        return sorted[lower] * (1.0 - weight) + sorted[upper] * weight
    }
}
