package com.vbtdroid.core.metrics

import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

data class Point2(val x: Double, val y: Double)

object JointAngles {
    fun angle(a: Point2, b: Point2, c: Point2): Double {
        val abX = a.x - b.x
        val abY = a.y - b.y
        val cbX = c.x - b.x
        val cbY = c.y - b.y
        val abLength = hypot(abX, abY)
        val cbLength = hypot(cbX, cbY)

        if (abLength == 0.0 || cbLength == 0.0) return 0.0

        val normalizedDot = ((abX / abLength) * (cbX / cbLength) +
            (abY / abLength) * (cbY / cbLength)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(normalizedDot))
    }

    fun correctAzimuth(imageAngleDeg: Double, cameraAngleDeg: Double): Double {
        if (cameraAngleDeg == 90.0 || imageAngleDeg == 90.0) return imageAngleDeg
        if (imageAngleDeg > 90.0) {
            return 180.0 - correctAzimuth(180.0 - imageAngleDeg, cameraAngleDeg)
        }

        val imageRadians = Math.toRadians(imageAngleDeg)
        val cameraRadians = Math.toRadians(cameraAngleDeg)
        return Math.toDegrees(atan(tan(imageRadians) / sin(cameraRadians)))
    }
}
