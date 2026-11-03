package com.vbtdroid.app.overlay

import com.vbtdroid.app.analysis.FrameAngles
import com.vbtdroid.app.vision.PoseFrame
import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.model.PlateObservation
import kotlin.math.abs

/**
 * Pure-JVM lookups shared by the overlay renderer and its tests. Every input list must be ordered
 * by ascending timestamp, matching the pipeline output.
 */
object OverlayTimeline {
    fun nearestObservation(
        observations: List<PlateObservation>,
        tSec: Double,
    ): PlateObservation? = observations.getOrNull(nearestIndex(observations.size, tSec) { observations[it].tSec })

    fun nearestPoseFrame(poseFrames: List<PoseFrame>, tSec: Double): PoseFrame? =
        poseFrames.getOrNull(nearestIndex(poseFrames.size, tSec) { poseFrames[it].tSec })

    fun nearestFrameAngles(frameAngles: List<FrameAngles>, tSec: Double): FrameAngles? =
        frameAngles.getOrNull(nearestIndex(frameAngles.size, tSec) { frameAngles[it].tSec })

    /** Observations inside `[tSec - windowSec, tSec]`, returned as a view without copying. */
    fun trail(
        observations: List<PlateObservation>,
        tSec: Double,
        windowSec: Double = 2.0,
    ): List<PlateObservation> {
        if (observations.isEmpty()) return emptyList()
        val safeWindow = abs(windowSec).coerceAtLeast(0.0)
        val end = upperBound(observations.size, tSec) { observations[it].tSec }
        if (end == 0) return emptyList()
        val start = lowerBound(observations.size, tSec - safeWindow) { observations[it].tSec }
        return observations.subList(start.coerceAtMost(end), end)
    }

    /** Absolute vertical bar velocity of the nearest motion sample. */
    fun speedMps(samples: List<MotionSample>, tSec: Double): Double? {
        val sample = samples.getOrNull(nearestIndex(samples.size, tSec) { samples[it].tSec }) ?: return null
        return abs(sample.vyMps)
    }

    private inline fun nearestIndex(size: Int, tSec: Double, timeAt: (Int) -> Double): Int {
        if (size == 0) return -1
        var low = 0
        var high = size - 1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val time = timeAt(mid)
            when {
                time < tSec -> low = mid + 1
                time > tSec -> high = mid - 1
                else -> return mid
            }
        }
        if (high < 0) return 0
        if (low >= size) return size - 1
        return if (tSec - timeAt(high) <= timeAt(low) - tSec) high else low
    }

    private inline fun lowerBound(size: Int, target: Double, timeAt: (Int) -> Double): Int {
        var low = 0
        var high = size
        while (low < high) {
            val mid = (low + high).ushr(1)
            if (timeAt(mid) < target) low = mid + 1 else high = mid
        }
        return low
    }

    private inline fun upperBound(size: Int, target: Double, timeAt: (Int) -> Double): Int {
        var low = 0
        var high = size
        while (low < high) {
            val mid = (low + high).ushr(1)
            if (timeAt(mid) <= target) low = mid + 1 else high = mid
        }
        return low
    }
}
