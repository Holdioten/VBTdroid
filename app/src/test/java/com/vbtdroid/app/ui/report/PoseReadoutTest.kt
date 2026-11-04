package com.vbtdroid.app.ui.report

import com.vbtdroid.app.analysis.FrameAngles
import com.vbtdroid.core.model.Lift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Regression: the pose card shows only lift-relevant joints and never a low-confidence number. */
class PoseReadoutTest {
    private fun frame(
        trunk: Double? = 40.0,
        hip: Double? = 90.0,
        knee: Double? = 110.0,
        ankle: Double? = 20.0,
        shoulder: Double? = 60.0,
        elbow: Double? = 75.0,
        confidence: Double = 0.9,
        perJoint: Map<String, Double> = emptyMap(),
    ) = FrameAngles(
        tSec = 1.0,
        hipDeg = hip,
        kneeDeg = knee,
        ankleDeg = ankle,
        trunkDeg = trunk,
        shoulderDeg = shoulder,
        elbowDeg = elbow,
        wristDeg = null,
        confidence = confidence,
        confidenceByJoint = perJoint,
    )

    @Test
    fun squatShowsBackHipKneeAnkle() {
        assertEquals(
            listOf(PoseJoint.BACK, PoseJoint.HIP, PoseJoint.KNEE, PoseJoint.ANKLE),
            PoseReadout.joints(Lift.SQUAT),
        )
    }

    @Test
    fun deadliftShowsBackHipKneeOnly() {
        assertEquals(listOf(PoseJoint.BACK, PoseJoint.HIP, PoseJoint.KNEE), PoseReadout.joints(Lift.DEADLIFT))
    }

    @Test
    fun benchShowsShoulderAndElbowOnly() {
        assertEquals(listOf(PoseJoint.SHOULDER, PoseJoint.ELBOW), PoseReadout.joints(Lift.BENCH))
    }

    @Test
    fun headlineJointFollowsLift() {
        assertEquals(PoseJoint.ELBOW, PoseReadout.headlineJoint(Lift.BENCH))
        assertEquals(PoseJoint.KNEE, PoseReadout.headlineJoint(Lift.SQUAT))
        assertEquals(PoseJoint.KNEE, PoseReadout.headlineJoint(Lift.DEADLIFT))
    }

    @Test
    fun highConfidenceValueIsShown() {
        assertEquals(40.0, PoseReadout.display(frame(), PoseJoint.BACK)!!, 1e-9)
    }

    @Test
    fun lowConfidenceJointIsHidden() {
        val f = frame(perJoint = mapOf("knee_angle_deg" to 0.3))
        assertNull(PoseReadout.display(f, PoseJoint.KNEE))
        assertEquals(90.0, PoseReadout.display(f, PoseJoint.HIP)!!, 1e-9)
    }

    @Test
    fun overallConfidenceIsUsedWhenPerJointMissing() {
        assertNull(PoseReadout.display(frame(confidence = 0.2), PoseJoint.BACK))
    }

    @Test
    fun missingOrNonFiniteValueIsHidden() {
        assertNull(PoseReadout.display(frame(trunk = null), PoseJoint.BACK))
        assertNull(PoseReadout.display(frame(trunk = Double.NaN), PoseJoint.BACK))
        assertNull(PoseReadout.display(null, PoseJoint.BACK))
    }

    @Test
    fun frameAtPicksNearestWithinGap() {
        val frames = listOf(frame().copy(tSec = 1.0), frame().copy(tSec = 1.2), frame().copy(tSec = 2.0))
        assertEquals(1.2, PoseReadout.frameAt(frames, 1.25)!!.tSec, 1e-9)
        assertNull(PoseReadout.frameAt(frames, 1.6))
        assertNull(PoseReadout.frameAt(emptyList(), 1.0))
        assertNull(PoseReadout.frameAt(frames, null))
    }
}
