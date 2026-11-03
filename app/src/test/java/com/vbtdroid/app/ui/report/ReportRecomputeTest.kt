package com.vbtdroid.app.ui.report

import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.analysis.SetSummary
import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.metrics.RepMetrics
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.phases.Phase
import com.vbtdroid.core.phases.PhaseType
import com.vbtdroid.core.phases.RepSlice
import com.vbtdroid.core.quality.QualityFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ReportRecomputeTest {
    @Test
    fun `manual recompute refreshes full set velocity loss`() {
        val previous = metrics(1, 0.5)
        val editedOriginal = metrics(2, 0.6)
        val future = metrics(3, 1.0)
        val result = result(listOf(previous, editedOriginal, future))
        val updated = concentricSlice(2, 0.0, 2.0, 0.0, 2.0)

        val recomputed = recomputeManualRep(result, updated)

        assertNotNull(recomputed)
        val edited = recomputed!!.reps.first { it.repIndex == 2 }
        assertEquals(0.75, edited.mcvMps, 1e-9)
        assertEquals(25.0, edited.velocityLossPct!!, 1e-9)
        assertEquals(50.0, recomputed.reps.first { it.repIndex == 1 }.velocityLossPct!!, 1e-9)
        assertEquals(0.0, recomputed.reps.first { it.repIndex == 3 }.velocityLossPct!!, 1e-9)
    }

    @Test
    fun `rejected manual edit returns null without changing source result`() {
        val previous = metrics(1, 0.5)
        val editedOriginal = metrics(2, 0.6)
        val result = result(listOf(previous, editedOriginal))
        val rejected = concentricSlice(2, 0.0, 2.0, 0.0, 0.5)

        val recomputed = recomputeManualRep(result, rejected)

        assertNull(recomputed)
        val lastValid = result.repSlices.single()
        val displayed = resolveManualRepEdit(rejected, lastValid) { false }
        assertSame(lastValid, displayed)
        assertSame(previous, result.reps[0])
        assertSame(editedOriginal, result.reps[1])
        assertEquals(listOf(0.0, 2.0), result.repSlices.single().phases.single().let {
            listOf(it.startSec, it.endSec)
        })
    }

    private fun result(reps: List<RepMetrics>): AnalysisResult = AnalysisResult(
        reps = reps,
        summary = SetSummary(
            repCount = reps.size,
            mcvAvgMps = null,
            bestMcvMps = null,
            velocityLossPct = null,
            totalVolumeKg = null,
            totalWorkJ = null,
        ),
        quality = QualityFlags(1.0, 0.0, 1.0, 1.0, true, emptyList()),
        fps = 10.0,
        motionSamples = motionSamples,
        repSlices = listOf(concentricSlice(2, 0.0, 2.0, 0.0, 2.0)),
        lift = Lift.SQUAT,
        loadKg = null,
    )

    private companion object {
        val motionSamples = listOf(
            motion(0.0, 0.0),
            motion(1.0, 0.5),
            motion(2.0, 1.5),
        )

        fun motion(tSec: Double, yM: Double) = MotionSample(
            tSec = tSec,
            xM = 0.0,
            yM = yM,
            vxMps = 0.0,
            vyMps = 0.5,
            axMps2 = 0.0,
            ayMps2 = 0.0,
        )

        fun concentricSlice(
            index: Int,
            startSec: Double,
            endSec: Double,
            concentricStart: Double,
            concentricEnd: Double,
        ) = RepSlice(
            index = index,
            startSec = startSec,
            endSec = endSec,
            phases = listOf(Phase(PhaseType.CONCENTRIC, concentricStart, concentricEnd)),
        )

        fun metrics(index: Int, mcv: Double) = RepMetrics(
            repIndex = index,
            durationS = 2.0,
            romTotalM = 1.5,
            romConcentricM = 1.5,
            meanVelocityMps = mcv,
            mcvMps = mcv,
            mpvMps = mcv,
            peakVelocityMps = mcv,
            peakAccelerationMps2 = 0.0,
            peakDecelerationMps2 = 0.0,
            stickingPointPct = null,
            velocityLossPct = null,
            barPathHorizontalRangeM = 0.0,
            barPathDeviationM = 0.0,
            meanForceN = null,
            peakForceN = null,
            meanPowerW = null,
            peakPowerW = null,
            impulseNs = null,
            workJ = null,
        )
    }
}
