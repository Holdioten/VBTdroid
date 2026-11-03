package com.vbtdroid.app.export

import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.analysis.SetSummary
import com.vbtdroid.core.metrics.RepMetrics
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.quality.QualityFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {
    private fun fakeResult(reps: Int): AnalysisResult {
        val metrics = (1..reps).map { i ->
            RepMetrics(
                repIndex = i, durationS = 1.0, romTotalM = 0.5, romConcentricM = 0.5,
                meanVelocityMps = 0.6, mcvMps = 0.6, mpvMps = 0.6, peakVelocityMps = 0.7,
                peakAccelerationMps2 = 1.0, peakDecelerationMps2 = -1.0,
                stickingPointPct = null, velocityLossPct = null,
                barPathHorizontalRangeM = 0.02, barPathDeviationM = 0.01,
                meanForceN = null, peakForceN = null, meanPowerW = null,
                peakPowerW = null, impulseNs = null, workJ = null,
            )
        }
        return AnalysisResult(
            reps = metrics,
            summary = SetSummary(reps, 0.6, 0.6, null, null, null),
            quality = QualityFlags(0.9, 0.0, 0.9, 0.9, true, emptyList()),
            fps = 60.0,
            lift = Lift.SQUAT,
        )
    }

    @Test
    fun `csv has header and one row per rep`() {
        val csv = CsvExporter.export(fakeResult(2))

        assertEquals("rep,mcv,mpv,pv,rom,vl,sticking,meanForce,meanPower", csv.lines().first())
        assertEquals(3, csv.lines().filter { it.isNotBlank() }.size)
    }

    @Test
    fun `null values export as empty fields`() {
        val row = CsvExporter.export(fakeResult(1)).lines()[1]

        assertEquals("1,0.6,0.6,0.7,0.5,,,,", row)
        assertTrue(row.contains(",,,"))
    }
}
