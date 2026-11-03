package com.vbtdroid.core.signal

object SavitzkyGolay {
    /** Smooths values with a local polynomial fit centered on each sample. */
    fun smooth(values: DoubleArray, window: Int = 7, polyOrder: Int = 2): DoubleArray {
        require(window > 0) { "window must be positive" }
        require(polyOrder >= 0) { "polyOrder must be non-negative" }
        if (values.isEmpty()) return doubleArrayOf()

        val times = DoubleArray(values.size) { it.toDouble() }
        return DoubleArray(values.size) { index ->
            LocalPolynomial.fit(times, values, index, window, polyOrder).value
        }
    }
}

internal object LocalPolynomial {
    data class Result(val value: Double, val firstDerivative: Double, val secondDerivative: Double?)

    fun fit(
        times: DoubleArray,
        values: DoubleArray,
        centerIndex: Int,
        window: Int,
        polyOrder: Int,
    ): Result {
        require(times.size == values.size) { "times and values must have equal lengths" }
        require(window > 0) { "window must be positive" }
        require(polyOrder >= 0) { "polyOrder must be non-negative" }
        require(centerIndex in times.indices) { "centerIndex must reference a sample" }

        val start = (centerIndex - window / 2).coerceAtLeast(0)
            .coerceAtMost((times.size - minOf(window, times.size)).coerceAtLeast(0))
        val end = (start + window).coerceAtMost(times.size)
        val count = end - start
        val degree = minOf(polyOrder, count - 1)
        val scale = (start until end).maxOf { kotlin.math.abs(times[it] - times[centerIndex]) }
        if (scale == 0.0 || !scale.isFinite()) {
            return Result(values[centerIndex], 0.0, null)
        }

        // Coordinates are normalized around the evaluation point to keep the fit conditioned.
        val columns = degree + 1
        val normal = Array(columns) { DoubleArray(columns) }
        val rhs = DoubleArray(columns)
        for (sampleIndex in start until end) {
            val u = (times[sampleIndex] - times[centerIndex]) / scale
            val powers = DoubleArray(columns)
            powers[0] = 1.0
            for (power in 1 until columns) powers[power] = powers[power - 1] * u
            for (row in 0 until columns) {
                rhs[row] += powers[row] * values[sampleIndex]
                for (column in 0 until columns) {
                    normal[row][column] += powers[row] * powers[column]
                }
            }
        }

        val coefficients = solve(normal, rhs)
        return Result(
            value = coefficients[0],
            firstDerivative = if (degree >= 1) coefficients[1] / scale else 0.0,
            secondDerivative = if (degree >= 2) 2.0 * coefficients[2] / (scale * scale) else null,
        )
    }

    private fun solve(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray {
        val size = vector.size
        val a = Array(size) { matrix[it].copyOf() }
        val b = vector.copyOf()

        for (pivot in 0 until size) {
            var best = pivot
            for (row in pivot + 1 until size) {
                if (kotlin.math.abs(a[row][pivot]) > kotlin.math.abs(a[best][pivot])) best = row
            }
            if (best != pivot) {
                val row = a[pivot]
                a[pivot] = a[best]
                a[best] = row
                val value = b[pivot]
                b[pivot] = b[best]
                b[best] = value
            }

            val divisor = a[pivot][pivot]
            for (column in pivot until size) a[pivot][column] /= divisor
            b[pivot] /= divisor
            for (row in 0 until size) {
                if (row == pivot) continue
                val factor = a[row][pivot]
                for (column in pivot until size) a[row][column] -= factor * a[pivot][column]
                b[row] -= factor * b[pivot]
            }
        }
        return b
    }
}
