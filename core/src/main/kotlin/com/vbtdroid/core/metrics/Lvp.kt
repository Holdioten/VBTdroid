package com.vbtdroid.core.metrics

data class LvpPoint(val loadKg: Double, val mcvMps: Double)

data class LvpModel(
    val slope: Double,
    val intercept: Double,
    val r2: Double,
    val v1rmMps: Double,
    val estimated1RmKg: Double
)

object Lvp {
    fun fit(points: List<LvpPoint>, v1rmMps: Double): LvpModel {
        require(points.size >= 3) { "At least three points are required" }
        require(points.map { it.loadKg }.distinct().size >= 2) { "At least two distinct loads are required" }
        require(v1rmMps > 0.0) { "v1rmMps must be positive" }

        val meanLoad = points.sumOf { it.loadKg } / points.size
        val meanVelocity = points.sumOf { it.mcvMps } / points.size
        val loadVariance = points.sumOf { (it.loadKg - meanLoad) * (it.loadKg - meanLoad) }
        val loadVelocityCovariance = points.sumOf {
            (it.loadKg - meanLoad) * (it.mcvMps - meanVelocity)
        }
        val slope = loadVelocityCovariance / loadVariance
        require(slope < 0.0) { "Load-velocity slope must be negative" }

        val intercept = meanVelocity - slope * meanLoad
        val ssRes = points.sumOf { point ->
            val residual = point.mcvMps - (intercept + slope * point.loadKg)
            residual * residual
        }
        val ssTot = points.sumOf { (it.mcvMps - meanVelocity) * (it.mcvMps - meanVelocity) }
        val r2 = if (ssTot == 0.0) 1.0 else 1.0 - ssRes / ssTot

        return LvpModel(
            slope = slope,
            intercept = intercept,
            r2 = r2,
            v1rmMps = v1rmMps,
            estimated1RmKg = (v1rmMps - intercept) / slope
        )
    }
}
