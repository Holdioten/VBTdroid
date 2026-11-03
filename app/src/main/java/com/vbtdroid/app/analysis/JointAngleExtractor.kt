package com.vbtdroid.app.analysis

import com.vbtdroid.app.vision.PoseFrame
import com.vbtdroid.core.metrics.JointAngles
import com.vbtdroid.core.metrics.Point2
import com.vbtdroid.core.model.Lift

data class FrameAngles(
    val tSec: Double,
    val hipDeg: Double?,
    val kneeDeg: Double?,
    val ankleDeg: Double?,
    val trunkDeg: Double?,
    val shoulderDeg: Double?,
    val elbowDeg: Double?,
    val wristDeg: Double?,
    val confidence: Double,
    val confidenceByJoint: Map<String, Double> = emptyMap(),
)

object JointAngleExtractor {
    private data class Side(
        val shoulder: Int,
        val elbow: Int,
        val wrist: Int,
        val index: Int,
        val hip: Int,
        val knee: Int,
        val ankle: Int,
        val footIndex: Int,
    ) {
        val landmarkIndices = listOf(shoulder, elbow, wrist, index, hip, knee, ankle, footIndex)
    }

    private val left = Side(11, 13, 15, 19, 23, 25, 27, 31)
    private val right = Side(12, 14, 16, 20, 24, 26, 28, 32)

    @Suppress("UNUSED_PARAMETER")
    fun extract(
        pose: PoseFrame,
        lift: Lift,
        cameraAngleDeg: Double,
        minVisibility: Float = 0.5f,
    ): FrameAngles {
        val side = if (averageVisibility(pose, left) >= averageVisibility(pose, right)) left else right
        val validAngleVisibilities = mutableListOf<Float>()
        val confidenceByJoint = mutableMapOf<String, Double>()

        fun point(index: Int): Point2? {
            val visibility = pose.visibility[index] ?: return null
            if (visibility < minVisibility) return null
            return pose.pixelPoint(index)
        }

        fun angle(name: String, a: Int, b: Int, c: Int): Double? {
            val pa = point(a) ?: return null
            val pb = point(b) ?: return null
            val pc = point(c) ?: return null
            val visibilities = listOfNotNull(pose.visibility[a], pose.visibility[b], pose.visibility[c])
            val raw = JointAngles.angle(pa, pb, pc) ?: return null
            validAngleVisibilities += visibilities
            confidenceByJoint[name] = visibilities.min().toDouble()
            return JointAngles.correctAzimuth(raw, cameraAngleDeg)
        }

        val hip = angle("hip_angle_deg", side.shoulder, side.hip, side.knee)
        val knee = angle("knee_angle_deg", side.hip, side.knee, side.ankle)
        val ankle = angle("ankle_angle_deg", side.knee, side.ankle, side.footIndex)
        val shoulder = angle("shoulder_angle_deg", side.hip, side.shoulder, side.elbow)
        val elbow = angle("elbow_angle_deg", side.shoulder, side.elbow, side.wrist)
        val wrist = angle("wrist_angle_deg", side.elbow, side.wrist, side.index)

        val trunk = run {
            val shoulderPoint = point(side.shoulder)
            val hipPoint = point(side.hip)
            if (shoulderPoint == null || hipPoint == null) {
                null
            } else {
                JointAngles.angle(
                    Point2(hipPoint.x, hipPoint.y - 1.0),
                    hipPoint,
                    shoulderPoint,
                )?.let { raw ->
                    val visibility = minOf(pose.visibility[side.shoulder] ?: 0f, pose.visibility[side.hip] ?: 0f)
                    validAngleVisibilities += visibility
                    confidenceByJoint["trunk_angle_deg"] = visibility.toDouble()
                    JointAngles.correctAzimuth(raw, cameraAngleDeg)
                }
            }
        }

        val confidence = validAngleVisibilities.minOrNull()?.toDouble() ?: 0.0
        return FrameAngles(
            tSec = pose.tSec,
            hipDeg = hip,
            kneeDeg = knee,
            ankleDeg = ankle,
            trunkDeg = trunk,
            shoulderDeg = shoulder,
            elbowDeg = elbow,
            wristDeg = wrist,
            confidence = confidence,
            confidenceByJoint = confidenceByJoint,
        )
    }

    private fun averageVisibility(pose: PoseFrame, side: Side): Float =
        side.landmarkIndices.map { index ->
            if (index in pose.landmarks) pose.visibility[index] ?: 0f else 0f
        }.average().toFloat()
}
