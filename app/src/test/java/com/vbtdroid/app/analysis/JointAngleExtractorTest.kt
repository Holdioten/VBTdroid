package com.vbtdroid.app.analysis

import com.vbtdroid.app.vision.PoseFrame
import com.vbtdroid.core.metrics.JointAngles
import com.vbtdroid.core.metrics.Point2
import com.vbtdroid.core.model.Lift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class JointAngleExtractorTest {
    @Test
    fun `missing knee landmark yields null knee angle with remaining angle confidence`() {
        val pose = PoseFrame(
            tSec = 0.0,
            landmarks = mapOf(11 to Point2(0.0, 0.0), 23 to Point2(0.0, 1.0)),
            visibility = mapOf(11 to 0.9f, 23 to 0.9f),
        )

        val angles = JointAngleExtractor.extract(pose, Lift.SQUAT, 45.0)

        assertNull(angles.kneeDeg)
        assertEquals(0.9, angles.confidence, 1e-6)
    }

    @Test
    fun `visible hip applies azimuth correction`() {
        val pose = PoseFrame(
            tSec = 0.0,
            landmarks = mapOf(
                11 to Point2(0.0, 0.0),
                23 to Point2(1.0, 0.0),
                25 to Point2(1.0, 1.0),
            ),
            visibility = mapOf(11 to 0.9f, 23 to 0.9f, 25 to 0.9f),
        )

        val angles = JointAngleExtractor.extract(pose, Lift.SQUAT, 45.0)

        assertEquals(90.0, angles.hipDeg!!, 1e-6)
    }

    @Test
    fun `normalized landmarks on 16 by 9 video use pixel aspect ratio`() {
        val pose = PoseFrame(
            tSec = 0.0,
            landmarks = mapOf(
                11 to Point2(0.0, 0.0),
                23 to Point2(0.5, 0.5),
                25 to Point2(0.5, 1.0),
            ),
            visibility = mapOf(11 to 0.9f, 23 to 0.9f, 25 to 0.9f),
            frameWidth = 1920,
            frameHeight = 1080,
            normalized = true,
        )
        val expected = JointAngles.angle(
            Point2(0.0, 0.0),
            Point2(960.0, 540.0),
            Point2(960.0, 1080.0),
        )!!

        val angles = JointAngleExtractor.extract(pose, Lift.SQUAT, 90.0)

        assertEquals(expected, angles.hipDeg!!, 1e-9)
        assertEquals(0.9, angles.confidenceByJoint.getValue("hip_angle_deg"), 1e-6)
    }

    @Test
    fun `non right hip angle applies azimuth correction`() {
        val pose = PoseFrame(
            tSec = 0.0,
            landmarks = mapOf(
                11 to Point2(0.0, 1.0),
                23 to Point2(0.0, 0.0),
                25 to Point2(0.70710678, 1.0),
            ),
            visibility = mapOf(11 to 0.9f, 23 to 0.9f, 25 to 0.9f),
        )

        val angles = JointAngleExtractor.extract(pose, Lift.SQUAT, 45.0)

        assertEquals(45.0, angles.hipDeg!!, 1e-6)
    }

    @Test
    fun `squat computes lower body and trunk angles only`() {
        val pose = lowerBodyPose()

        val angles = JointAngleExtractor.extract(pose, Lift.SQUAT, 90.0)

        assertEquals(90.0, angles.hipDeg!!, 1e-6)
        assertEquals(90.0, angles.kneeDeg!!, 1e-6)
        assertNotNull(angles.ankleDeg)
        assertNotNull(angles.trunkDeg)
        assertNull(angles.shoulderDeg)
        assertNull(angles.elbowDeg)
        assertNull(angles.wristDeg)
        assertEquals(
            setOf("hip_angle_deg", "knee_angle_deg", "ankle_angle_deg", "trunk_angle_deg"),
            angles.confidenceByJoint.keys,
        )
    }

    @Test
    fun `deadlift computes lower body and trunk angles only`() {
        val pose = lowerBodyPose()

        val angles = JointAngleExtractor.extract(pose, Lift.DEADLIFT, 90.0)

        assertEquals(90.0, angles.hipDeg!!, 1e-6)
        assertEquals(90.0, angles.kneeDeg!!, 1e-6)
        assertNotNull(angles.ankleDeg)
        assertNotNull(angles.trunkDeg)
        assertNull(angles.shoulderDeg)
        assertNull(angles.elbowDeg)
        assertNull(angles.wristDeg)
        assertEquals(
            setOf("hip_angle_deg", "knee_angle_deg", "ankle_angle_deg", "trunk_angle_deg"),
            angles.confidenceByJoint.keys,
        )
    }

    @Test
    fun `bench computes torso and arm angles only`() {
        val pose = PoseFrame(
            tSec = 0.0,
            landmarks = mapOf(
                11 to Point2(0.0, 0.0),
                13 to Point2(0.0, 1.0),
                15 to Point2(1.0, 1.0),
                23 to Point2(0.0, 2.0),
            ),
            visibility = mapOf(11 to 0.9f, 13 to 0.9f, 15 to 0.9f, 23 to 0.9f),
        )

        val angles = JointAngleExtractor.extract(pose, Lift.BENCH, 90.0)

        assertNotNull(angles.shoulderDeg)
        assertNotNull(angles.elbowDeg)
        assertNotNull(angles.trunkDeg)
        assertNull(angles.hipDeg)
        assertNull(angles.kneeDeg)
        assertNull(angles.ankleDeg)
        assertNull(angles.wristDeg)
        assertEquals(
            setOf("shoulder_angle_deg", "elbow_angle_deg", "trunk_angle_deg"),
            angles.confidenceByJoint.keys,
        )
    }

    private fun lowerBodyPose() = PoseFrame(
        tSec = 0.0,
        landmarks = mapOf(
            11 to Point2(0.0, 0.0),
            23 to Point2(0.0, 1.0),
            25 to Point2(1.0, 1.0),
            27 to Point2(1.0, 2.0),
            31 to Point2(2.0, 2.0),
        ),
        visibility = mapOf(11 to 0.9f, 23 to 0.9f, 25 to 0.9f, 27 to 0.9f, 31 to 0.9f),
    )
}
