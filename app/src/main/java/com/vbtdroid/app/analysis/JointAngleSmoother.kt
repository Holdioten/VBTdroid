package com.vbtdroid.app.analysis

/**
 * Median smoothing for pose-derived joint angles.
 *
 * Runs after [JointAngleExtractor] so isolated landmark errors cannot make the displayed angle
 * flicker. A missing angle stays missing: nulls are never filled from neighbouring frames, they
 * only contribute to the median when the current frame itself has a value.
 */
object JointAngleSmoother {
    const val DEFAULT_WINDOW = 3

    fun smooth(
        frames: List<FrameAngles>,
        window: Int = DEFAULT_WINDOW,
    ): List<FrameAngles> {
        require(window > 0 && window % 2 == 1) { "window must be a positive odd number" }
        if (frames.isEmpty() || window == 1) return frames

        val radius = window / 2
        return frames.mapIndexed { index, frame ->
            val neighbours = (-radius..radius).map { offset ->
                frames[(index + offset).coerceIn(0, frames.lastIndex)]
            }
            frame.copy(
                hipDeg = median(neighbours.map { it.hipDeg }, frame.hipDeg),
                kneeDeg = median(neighbours.map { it.kneeDeg }, frame.kneeDeg),
                ankleDeg = median(neighbours.map { it.ankleDeg }, frame.ankleDeg),
                trunkDeg = median(neighbours.map { it.trunkDeg }, frame.trunkDeg),
                shoulderDeg = median(neighbours.map { it.shoulderDeg }, frame.shoulderDeg),
                elbowDeg = median(neighbours.map { it.elbowDeg }, frame.elbowDeg),
                wristDeg = median(neighbours.map { it.wristDeg }, frame.wristDeg),
            )
        }
    }

    private fun median(values: List<Double?>, current: Double?): Double? {
        if (current == null) return null
        val present = values.filterNotNull().sorted()
        if (present.isEmpty()) return null
        return present[present.size / 2]
    }
}
