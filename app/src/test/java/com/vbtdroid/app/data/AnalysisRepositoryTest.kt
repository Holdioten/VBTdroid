package com.vbtdroid.app.data

import android.graphics.RectF
import android.net.Uri
import androidx.room.Room
import com.vbtdroid.app.analysis.AnalysisRequest
import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.analysis.SetSummary
import com.vbtdroid.app.export.ExportMapper
import com.vbtdroid.core.calibration.Calibration
import com.vbtdroid.core.io.JsonExport
import com.vbtdroid.core.metrics.Lvp
import com.vbtdroid.core.metrics.RepMetrics
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.model.LiftVariant
import com.vbtdroid.core.phases.Phase
import com.vbtdroid.core.phases.PhaseType
import com.vbtdroid.core.phases.RepSlice
import com.vbtdroid.core.quality.QualityFlags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AnalysisRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AnalysisRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = AnalysisRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `save persists complete graph and aggregates only same lift variant`() = runBlocking {
        repository.saveAnalysis(result(100.0, 0.8, 1L), request(100.0))
        repository.saveAnalysis(result(120.0, 0.65, 2L), request(120.0))
        repository.saveAnalysis(result(100.0, 0.75, 3L), request(100.0, LiftVariant.LOW_BAR))

        val saved = repository.saveAnalysis(result(140.0, 0.5, 4L), request(140.0))

        // Automatic LVP was removed: saving a set must not derive an estimate on its own.
        assertNull(saved.lvp)
        assertEquals(4, repository.history().size)
        assertEquals(3, repository.sameLiftVariant(Lift.SQUAT, LiftVariant.HIGH_BAR).size)
        assertEquals(1, repository.sameLoad(Lift.SQUAT, LiftVariant.HIGH_BAR, 100.0).size)

        val detail = repository.detail(repository.history().first().setId)
        assertNotNull(detail)
        assertEquals(1, detail!!.reps.size)
        assertTrue(detail.phases.values.single().single().manual)
        val export = JsonExport.decode(detail.set.exportJson)
        assertEquals("content://test/video", export.videoUri)
        assertEquals(450.0, export.plateDiameterMm, 1e-9)
        assertEquals(90.0, export.cameraAngleDeg, 1e-9)
        assertTrue(export.calibration!!.cameraLevel)
        assertEquals(140.0, export.summary!!.totalVolumeKg!!, 1e-9)
        assertEquals("high_bar", export.variant)
    }

    @Test
    fun `manual phase edits are persisted for a saved set`() = runBlocking {
        val saved = repository.saveAnalysis(result(100.0, 0.8, 1L), request(100.0))
        val edited = saved.copy(
            repSlices = listOf(
                RepSlice(
                    1,
                    0.0,
                    1.5,
                    listOf(Phase(PhaseType.CONCENTRIC, 0.0, 1.5, manual = true)),
                ),
            ),
            reps = saved.reps.map { it.copy(durationS = 1.5) },
        )

        assertTrue(repository.updateSavedAnalysis(edited))

        val detail = repository.detail(saved.savedSetId!!)!!
        val phase = detail.phases.values.single().single()
        assertEquals(1.5, phase.endSec, 1e-9)
        assertTrue(phase.manual)
    }

    @Test
    fun `saving a set exports no lvp estimate`() = runBlocking {
        val saved = repository.saveAnalysis(result(100.0, 0.8, 1L), request(100.0))
        val export = ExportMapper.toSetExport(saved)

        // 1RM is produced by the manual estimator, never inferred while saving.
        assertNull(export.lvp)
    }

    private fun request(load: Double, variant: LiftVariant = LiftVariant.HIGH_BAR) = AnalysisRequest(
        uri = Uri.parse("content://test/video"),
        lift = Lift.SQUAT,
        variant = variant,
        cameraAngleDeg = 90.0,
        plateDiameterM = 0.45,
        loadKg = load,
        roi = RectF(0f, 0f, 100f, 100f),
    )

    private fun result(load: Double, mcv: Double, createdAt: Long): AnalysisResult {
        val rep = RepMetrics(
            repIndex = 1,
            durationS = 2.0,
            romTotalM = 0.5,
            romConcentricM = 0.5,
            meanVelocityMps = 0.0,
            mcvMps = mcv,
            mpvMps = mcv,
            peakVelocityMps = mcv,
            peakAccelerationMps2 = 0.0,
            peakDecelerationMps2 = 0.0,
            stickingPointPct = null,
            velocityLossPct = 0.0,
            barPathHorizontalRangeM = 0.0,
            barPathDeviationM = 0.0,
            meanForceN = load * 9.80665,
            peakForceN = load * 9.80665,
            meanPowerW = load * 9.80665 * mcv,
            peakPowerW = load * 9.80665 * mcv,
            impulseNs = load * 9.80665,
            workJ = load * 9.80665 * 0.5,
        )
        return AnalysisResult(
            reps = listOf(rep),
            summary = SetSummary(1, mcv, mcv, 0.0, load, rep.workJ),
            quality = QualityFlags(0.95, 0.0, 0.95, 0.95, true, emptyList()),
            fps = 60.0,
            repSlices = listOf(
                RepSlice(
                    1,
                    0.0,
                    2.0,
                    listOf(Phase(PhaseType.CONCENTRIC, 0.0, 2.0, manual = true)),
                ),
            ),
            lift = Lift.SQUAT,
            variant = LiftVariant.HIGH_BAR,
            loadKg = load,
            videoUri = "content://test/video",
            calibration = Calibration.fromPlate(0.45, 225.0, 90.0),
            createdAtEpochMs = createdAt,
        )
    }
}
