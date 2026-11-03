package com.vbtdroid.core.validation

import kotlin.math.sqrt

data class AgreementResult(
    val bias: Double,
    val sdDiff: Double,
    val lowerLoa: Double,
    val upperLoa: Double,
    val icc: Double,
)

object Agreement {
    /** Computes Bland–Altman limits and two-way random, absolute-agreement ICC(2,1). */
    fun blandAltman(reference: DoubleArray, measured: DoubleArray): AgreementResult {
        require(reference.isNotEmpty()) { "At least one paired measurement is required" }
        require(reference.size == measured.size) { "Measurement arrays must have equal lengths" }

        val differences = measured.indices.map { measured[it] - reference[it] }
        val bias = differences.average()
        val sdDiff = if (differences.size > 1) {
            sqrt(differences.sumOf { (it - bias) * (it - bias) } / (differences.size - 1))
        } else {
            0.0
        }
        val margin = 1.96 * sdDiff

        return AgreementResult(
            bias = bias,
            sdDiff = sdDiff,
            lowerLoa = bias - margin,
            upperLoa = bias + margin,
            icc = iccTwoWayAbsolute(reference, measured),
        )
    }

    private fun iccTwoWayAbsolute(reference: DoubleArray, measured: DoubleArray): Double {
        val n = reference.size
        if (reference.indices.all { reference[it] == measured[it] }) return 1.0
        if (n < 2) return 0.0

        val grandMean = (reference.sum() + measured.sum()) / (2.0 * n)
        val referenceMean = reference.average()
        val measuredMean = measured.average()
        val rowMeans = reference.indices.map { (reference[it] + measured[it]) / 2.0 }
        val msRows = 2.0 * rowMeans.sumOf { (it - grandMean) * (it - grandMean) } / (n - 1)
        val msColumns = n * ((referenceMean - grandMean) * (referenceMean - grandMean) +
            (measuredMean - grandMean) * (measuredMean - grandMean))
        val residualSumSquares = reference.indices.sumOf { index ->
            val rowMean = rowMeans[index]
            (reference[index] - rowMean - referenceMean + grandMean).let { it * it } +
                (measured[index] - rowMean - measuredMean + grandMean).let { it * it }
        }
        val msError = residualSumSquares / (n - 1)
        val denominator = msRows + msError + 2.0 * (msColumns - msError) / n
        return if (denominator == 0.0) 0.0 else (msRows - msError) / denominator
    }
}
