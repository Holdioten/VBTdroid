package com.vbtdroid.app.vision

import com.vbtdroid.core.metrics.Point2
import kotlin.math.hypot

/**
 * One detected person with landmarks already converted to frame pixels.
 *
 * Multiple people can appear in a lift video (gym background). The selector below decides which
 * one is the lifter so joint angles are not computed from a bystander.
 */
data class PoseCandidate(
    val landmarks: Map<Int, Point2>,
    val visibility: Map<Int, Float> = emptyMap(),
) {
    /** Midpoint between the hip midpoint and the shoulder midpoint. */
    fun torsoMidpoint(): Point2? {
        val shoulders = midpointOf(SHOULDER_LEFT, SHOULDER_RIGHT)
        val hips = midpointOf(HIP_LEFT, HIP_RIGHT)
        return when {
            shoulders != null && hips != null -> Point2(
                (shoulders.x + hips.x) / 2.0,
                (shoulders.y + hips.y) / 2.0,
            )
            shoulders != null -> shoulders
            hips != null -> hips
            else -> centroid()
        }
    }

    /** Bounding-box diagonal; a cheap proxy for how near/large the skeleton is. */
    fun skeletonSizePx(): Double {
        if (landmarks.isEmpty()) return 0.0
        val xs = landmarks.values.map { it.x }
        val ys = landmarks.values.map { it.y }
        return hypot(xs.max() - xs.min(), ys.max() - ys.min())
    }

    private fun midpointOf(first: Int, second: Int): Point2? {
        val a = landmarks[first] ?: return null
        val b = landmarks[second] ?: return null
        return Point2((a.x + b.x) / 2.0, (a.y + b.y) / 2.0)
    }

    private fun centroid(): Point2? {
        if (landmarks.isEmpty()) return null
        return Point2(
            landmarks.values.sumOf { it.x } / landmarks.size,
            landmarks.values.sumOf { it.y } / landmarks.size,
        )
    }

    companion object {
        const val SHOULDER_LEFT = 11
        const val SHOULDER_RIGHT = 12
        const val HIP_LEFT = 23
        const val HIP_RIGHT = 24
    }
}

/**
 * Pure selection policy for multi-person pose output.
 *
 * With a bar hint the torso midpoint closest to the bar wins. Candidates within a small distance
 * band are treated as tied and the larger (nearer) skeleton wins, which avoids flipping to a small
 * distant bystander when two detections nearly overlap the bar. Without a bar hint the largest
 * skeleton is used as a best-effort lifter guess.
 */
object PosePersonSelector {
    /** Relative distance band where skeleton size breaks the tie. */
    const val DISTANCE_TIE_BAND = 0.10

    /** Absolute distance band in pixels, so tiny distances still have a stable tie-break. */
    const val DISTANCE_TIE_SLACK_PX = 4.0

    /**
     * @param barCenter plate/bar centre in frame pixels, or null when no observation exists.
     * @param maxDistancePx reject candidates farther than this from [barCenter]; null disables the
     *   gate. Callers should pass a generous value: a real lifter's torso is near the bar, while a
     *   background person is usually well outside one plate diameter.
     * @return index into [candidates], or null when nothing passes.
     */
    fun select(
        candidates: List<PoseCandidate>,
        barCenter: Point2?,
        maxDistancePx: Double? = null,
    ): Int? {
        if (candidates.isEmpty()) return null

        if (barCenter == null) {
            return candidates.indices.maxByOrNull { candidates[it].skeletonSizePx() }
        }

        val distances = candidates.map { candidate ->
            candidate.torsoMidpoint()?.let { midpoint ->
                hypot(midpoint.x - barCenter.x, midpoint.y - barCenter.y)
            }
        }

        val eligible = candidates.indices.filter { index ->
            val distance = distances[index] ?: return@filter false
            distance.isFinite() && (maxDistancePx == null || distance <= maxDistancePx)
        }
        if (eligible.isEmpty()) return null

        val nearest = eligible.minOf { distances[it]!! }
        val band = maxOf(DISTANCE_TIE_SLACK_PX, nearest * DISTANCE_TIE_BAND)
        return eligible
            .filter { distances[it]!! <= nearest + band }
            .maxByOrNull { candidates[it].skeletonSizePx() }
    }
}
