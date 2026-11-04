package com.vbtdroid.app.ui.report

import com.vbtdroid.app.R
import com.vbtdroid.app.analysis.FrameAngles
import com.vbtdroid.core.model.Lift

/** One joint shown on the 姿态分析 card. [key] matches the FrameAngles confidence map. */
internal enum class PoseJoint(val key: String, val labelRes: Int) {
    BACK("trunk_angle_deg", R.string.metric_trunk_angle),
    HIP("hip_angle_deg", R.string.metric_hip_angle),
    KNEE("knee_angle_deg", R.string.metric_knee_angle),
    ANKLE("ankle_angle_deg", R.string.metric_ankle_angle),
    SHOULDER("shoulder_angle_deg", R.string.metric_shoulder_angle),
    ELBOW("elbow_angle_deg", R.string.metric_elbow_angle),
    ;

    /** Raw angle for this joint in one frame. */
    fun valueIn(frame: FrameAngles): Double? = when (this) {
        BACK -> frame.trunkDeg
        HIP -> frame.hipDeg
        KNEE -> frame.kneeDeg
        ANKLE -> frame.ankleDeg
        SHOULDER -> frame.shoulderDeg
        ELBOW -> frame.elbowDeg
    }
}

internal object PoseReadout {
    /** Below this confidence a joint is shown as “—” instead of a number. */
    const val MIN_CONFIDENCE = 0.5

    /** Largest distance between playhead and pose frame still treated as "current". */
    const val MAX_FRAME_GAP_SEC = 0.3

    /** Joints shown for each lift, in display order. */
    fun joints(lift: Lift): List<PoseJoint> = when (lift) {
        Lift.SQUAT -> listOf(PoseJoint.BACK, PoseJoint.HIP, PoseJoint.KNEE, PoseJoint.ANKLE)
        Lift.DEADLIFT -> listOf(PoseJoint.BACK, PoseJoint.HIP, PoseJoint.KNEE)
        Lift.BENCH -> listOf(PoseJoint.SHOULDER, PoseJoint.ELBOW)
    }

    /** Joint whose curve is drawn on the card: knee for squat/deadlift, elbow for bench. */
    fun headlineJoint(lift: Lift): PoseJoint = if (lift == Lift.BENCH) PoseJoint.ELBOW else PoseJoint.KNEE

    /**
     * Frame nearest to [tSec], or null when none lies within [maxGapSec]. The gap limit keeps a
     * stale frame from being shown as the current pose.
     */
    fun frameAt(frames: List<FrameAngles>, tSec: Double?, maxGapSec: Double = MAX_FRAME_GAP_SEC): FrameAngles? {
        if (tSec == null || !tSec.isFinite() || frames.isEmpty()) return null
        val nearest = frames.minByOrNull { kotlin.math.abs(it.tSec - tSec) } ?: return null
        return nearest.takeIf { kotlin.math.abs(it.tSec - tSec) <= maxGapSec }
    }

    /** Angle to display for [joint] in [frame]; null when missing, non-finite, or low confidence. */
    fun display(frame: FrameAngles?, joint: PoseJoint): Double? {
        if (frame == null) return null
        val value = joint.valueIn(frame)?.takeIf { it.isFinite() } ?: return null
        val confidence = frame.confidenceByJoint[joint.key] ?: frame.confidence
        return value.takeIf { confidence.isFinite() && confidence >= MIN_CONFIDENCE }
    }
}
