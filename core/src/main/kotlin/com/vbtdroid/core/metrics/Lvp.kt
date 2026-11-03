package com.vbtdroid.core.metrics

import com.vbtdroid.core.model.Lift

data class LvpPoint(val loadKg: Double, val mcvMps: Double)

data class LvpModel(
    val slope: Double,
    val intercept: Double,
    val r2: Double,
    val v1rmMps: Double,
    val estimated1RmKg: Double,
)

data class LvpEstimate(
    val model: LvpModel? = null,
    val v1rmMps: Double,
    val v1rmSource: String = "literature",
    val pointCount: Int = 0,
    val unavailableReason: String? = null,
)

object Lvp {
    const val INSUFFICIENT_LOAD_POINTS = "LVP_REQUIRES_THREE_DISTINCT_LOADS"
    const val INVALID_FIT = "LVP_INVALID_FIT"

    fun defaultV1rmMps(lift: Lift): Double = when (lift) {
        Lift.SQUAT -> 0.30
        Lift.BENCH -> 0.17
        Lift.DEADLIFT -> 0.25
    }

    /** Averages repeated sets at one load, then requires at least three distinct loads. */
    fun estimate(
        points: List<LvpPoint>,
        lift: Lift,
        v1rmMps: Double = defaultV1rmMps(lift),
        v1rmSource: String = "literature",
    ): LvpEstimate {
        val validPoints = points.filter {
            it.loadKg.isFinite() && it.loadKg > 0.0 && it.mcvMps.isFinite() && it.mcvMps > 0.0
        }
        val aggregated = validPoints.groupBy { it.loadKg }.map { (load, sameLoad) ->
            LvpPoint(load, sameLoad.map { it.mcvMps }.average())
        }.sortedBy { it.loadKg }
        if (aggregated.size < 3) {
            return LvpEstimate(
                v1rmMps = v1rmMps,
                v1rmSource = v1rmSource,
                pointCount = aggregated.size,
                unavailableReason = INSUFFICIENT_LOAD_POINTS,
            )
        }
        val model = runCatching { fit(aggregated, v1rmMps) }.getOrNull()
        return LvpEstimate(
            model = model,
            v1rmMps = v1rmMps,
            v1rmSource = v1rmSource,
            pointCount = aggregated.size,
            unavailableReason = if (model == null) INVALID_FIT else null,
        )
    }

    fun fit(points: List<LvpPoint>, v1rmMps: Double): LvpModel {
        require(points.size >= 3) { "At least three points are required" }
        require(points.all { it.loadKg.isFinite() && it.loadKg > 0.0 && it.mcvMps.isFinite() }) {
            "Load-velocity points must be finite and loads must be positive"
        }
        require(points.map { it.loadKg }.distinct().size >= 3) { "At least three distinct loads are required" }
        require(v1rmMps.isFinite() && v1rmMps > 0.0) { "v1rmMps must be positive and finite" }

        val meanLoad = points.sumOf { it.loadKg } / points.size
        val meanVelocity = points.sumOf { it.mcvMps } / points.size
        val loadVariance = points.sumOf { (it.loadKg - meanLoad) * (it.loadKg - meanLoad) }
        require(loadVariance > 0.0) { "Load variance must be positive" }
        val loadVelocityCovariance = points.sumOf {
            (it.loadKg - meanLoad) * (it.mcvMps - meanVelocity)
        }
        val slope = loadVelocityCovariance / loadVariance
        require(slope.isFinite() && slope < 0.0) { "Load-velocity slope must be finite and negative" }

        val intercept = meanVelocity - slope * meanLoad
        val ssRes = points.sumOf { point ->
            val residual = point.mcvMps - (intercept + slope * point.loadKg)
            residual * residual
        }
        val ssTot = points.sumOf { (it.mcvMps - meanVelocity) * (it.mcvMps - meanVelocity) }
        val r2 = if (ssTot == 0.0) Double.NaN else 1.0 - ssRes / ssTot
        val estimated1RmKg = (v1rmMps - intercept) / slope
        require(intercept.isFinite() && r2.isFinite() && estimated1RmKg.isFinite() && estimated1RmKg > 0.0) {
            "LVP must produce finite, positive estimates"
        }

        return LvpModel(
            slope = slope,
            intercept = intercept,
            r2 = r2,
            v1rmMps = v1rmMps,
            estimated1RmKg = estimated1RmKg,
        )
    }
}
