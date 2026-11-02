package com.vbtdroid.core.model

enum class Lift { SQUAT, BENCH, DEADLIFT }

data class PlateObservation(
    val tSec: Double,
    val xPx: Double,
    val yPx: Double,
    val majorAxisPx: Double,
    val confidence: Double,
)

data class BarSample(val tSec: Double, val xM: Double, val yM: Double)
