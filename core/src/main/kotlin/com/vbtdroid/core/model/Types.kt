package com.vbtdroid.core.model

enum class Lift(val exportId: String) {
    SQUAT("back_squat"),
    BENCH("bench_press"),
    DEADLIFT("deadlift"),
}

/** Lift variants are part of the history/LVP identity; never mix variants in one fit. */
enum class LiftVariant(val exportId: String) {
    DEFAULT("default"),
    HIGH_BAR("high_bar"),
    LOW_BAR("low_bar"),
    CONVENTIONAL("conventional"),
    SUMO("sumo"),
    PAUSED("paused"),
    TOUCH_AND_GO("touch_and_go"),
}

data class PlateObservation(
    val tSec: Double,
    val xPx: Double,
    val yPx: Double,
    val majorAxisPx: Double,
    val confidence: Double,
)

data class BarSample(val tSec: Double, val xM: Double, val yM: Double)
