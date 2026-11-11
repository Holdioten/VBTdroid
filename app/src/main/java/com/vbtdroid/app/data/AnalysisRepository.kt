package com.vbtdroid.app.data

import androidx.room.withTransaction
import com.vbtdroid.app.analysis.AnalysisRequest
import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.export.ExportMapper
import com.vbtdroid.core.io.JsonExport
import com.vbtdroid.core.metrics.Lvp
import com.vbtdroid.core.metrics.LvpEstimate
import com.vbtdroid.core.metrics.LvpPoint
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.model.LiftVariant

data class HistoryDetail(
    val set: SetEntity,
    val reps: List<RepEntity>,
    val phases: Map<String, List<PhaseEntity>>,
)

class AnalysisRepository(private val database: AppDatabase) {
    private val dao = database.sessionDao()

    /** Saves the complete object graph and recomputes only the matching lift/variant profile. */
    suspend fun saveAnalysis(result: AnalysisResult, request: AnalysisRequest): AnalysisResult =
        database.withTransaction {
            val createdAt = result.createdAtEpochMs
            val session = SessionEntity(
                startedAtEpochMs = createdAt,
                lift = request.lift.name,
                variant = request.variant.name,
            )
            dao.insertSession(session)

            val initialExport = ExportMapper.toSetExport(result, request)
            val set = SetEntity(
                sessionId = session.sessionId,
                lift = request.lift.name,
                variant = request.variant.name,
                createdAtEpochMs = createdAt,
                loadKg = request.loadKg,
                plateDiameterMm = request.plateDiameterM * 1000.0,
                cameraAngleDeg = request.cameraAngleDeg,
                cameraLevel = request.cameraLevel,
                videoUri = request.uri.toString(),
                mcvAvgMps = result.summary.mcvAvgMps,
                metricsJson = JsonExport.encode(initialExport),
                qualityJson = JsonExport.encode(initialExport),
                exportJson = JsonExport.encode(initialExport),
            )
            dao.insertSet(set)

            val repEntities = initialExport.reps.map { rep ->
                RepEntity(
                    setId = set.setId,
                    repIndex = rep.index,
                    startSec = rep.phases.minOfOrNull { it.startSec } ?: 0.0,
                    endSec = rep.phases.maxOfOrNull { it.endSec } ?: 0.0,
                    metricsJson = JsonExport.encodeRep(rep),
                    qualityJson = JsonExport.encodeRep(rep),
                )
            }
            dao.insertReps(repEntities)
            val phaseEntities = initialExport.reps.flatMapIndexed { repIndex, rep ->
                rep.phases.mapIndexed { phaseIndex, phase ->
                    PhaseEntity(
                        repId = repEntities[repIndex].repId,
                        phaseIndex = phaseIndex,
                        type = phase.type,
                        startSec = phase.startSec,
                        endSec = phase.endSec,
                        manual = phase.manual,
                    )
                }
            }
            if (phaseEntities.isNotEmpty()) dao.insertPhases(phaseEntities)

            val finalResult = result.copy(savedSetId = set.setId)
            val finalExport = ExportMapper.toSetExport(finalResult, request, finalResult.lvp)
            val encoded = JsonExport.encode(finalExport)
            dao.updateSet(set.copy(metricsJson = encoded, qualityJson = encoded, exportJson = encoded))
            finalResult
        }

    /** Persists manual phase edits and refreshed metrics for a previously saved set. */
    suspend fun updateSavedAnalysis(result: AnalysisResult): Boolean {
        val setId = result.savedSetId ?: return false
        return database.withTransaction {
            val existing = dao.setById(setId) ?: return@withTransaction false
            val export = ExportMapper.toSetExport(result)
            val encoded = JsonExport.encode(export)
            dao.deleteRepsForSet(setId)
            val repEntities = export.reps.map { rep ->
                RepEntity(
                    setId = setId,
                    repIndex = rep.index,
                    startSec = rep.phases.minOfOrNull { it.startSec } ?: 0.0,
                    endSec = rep.phases.maxOfOrNull { it.endSec } ?: 0.0,
                    metricsJson = JsonExport.encodeRep(rep),
                    qualityJson = JsonExport.encodeRep(rep),
                )
            }
            if (repEntities.isNotEmpty()) dao.insertReps(repEntities)
            val phaseEntities = export.reps.flatMapIndexed { repIndex, rep ->
                rep.phases.mapIndexed { phaseIndex, phase ->
                    PhaseEntity(
                        repId = repEntities[repIndex].repId,
                        phaseIndex = phaseIndex,
                        type = phase.type,
                        startSec = phase.startSec,
                        endSec = phase.endSec,
                        manual = phase.manual,
                    )
                }
            }
            if (phaseEntities.isNotEmpty()) dao.insertPhases(phaseEntities)
            dao.updateSet(
                existing.copy(
                    mcvAvgMps = result.summary.mcvAvgMps,
                    metricsJson = encoded,
                    qualityJson = encoded,
                    exportJson = encoded,
                ),
            ) > 0
        }
    }

    suspend fun history(): List<SetEntity> = dao.history()

    suspend fun detail(setId: String): HistoryDetail? {
        val set = dao.setById(setId) ?: return null
        val reps = dao.repsForSet(setId)
        return HistoryDetail(
            set = set,
            reps = reps,
            phases = reps.associate { it.repId to dao.phasesForRep(it.repId) },
        )
    }

    suspend fun sameLiftVariant(lift: Lift, variant: LiftVariant): List<SetEntity> =
        dao.setsForLiftVariant(lift.name, variant.name)

    /** Same-lift, same-variant rows at one external load, newest first. */
    suspend fun sameLoad(lift: Lift, variant: LiftVariant, loadKg: Double): List<SetEntity> =
        dao.setsForLiftVariantLoad(lift.name, variant.name, loadKg)

    suspend fun saveOneRmEstimate(estimate: OneRmEstimateEntity) {
        database.sessionDao().upsertOneRmEstimate(estimate)
    }

    suspend fun oneRmEstimates(): List<OneRmEstimateEntity> = database.sessionDao().oneRmEstimates()

    suspend fun profile(lift: Lift, variant: LiftVariant): LvpEstimate {
        val stored = dao.liftProfile(lift.name, variant.name)
        return stored?.toEstimate() ?: aggregateLvp(lift, variant)
    }

    private suspend fun aggregateLvp(lift: Lift, variant: LiftVariant): LvpEstimate {
        val points = dao.loadVelocityPoints(lift.name, variant.name).map {
            LvpPoint(it.loadKg, it.mcvAvgMps)
        }
        val estimate = Lvp.estimate(points, lift)
        dao.upsertLiftProfile(
            LiftProfileEntity(
                lift = lift.name,
                variant = variant.name,
                v1rmMps = estimate.v1rmMps,
                v1rmSource = estimate.v1rmSource,
                slope = estimate.model?.slope,
                intercept = estimate.model?.intercept,
                r2 = estimate.model?.r2,
                estimated1RmKg = estimate.model?.estimated1RmKg,
                pointCount = estimate.pointCount,
                unavailableReason = estimate.unavailableReason,
            ),
        )
        return estimate
    }

    private fun LiftProfileEntity.toEstimate(): LvpEstimate = LvpEstimate(
        model = if (slope != null && intercept != null && r2 != null && estimated1RmKg != null) {
            com.vbtdroid.core.metrics.LvpModel(
                slope = slope,
                intercept = intercept,
                r2 = r2,
                v1rmMps = v1rmMps,
                estimated1RmKg = estimated1RmKg,
            )
        } else {
            null
        },
        v1rmMps = v1rmMps,
        v1rmSource = v1rmSource,
        pointCount = pointCount,
        unavailableReason = unavailableReason,
    )
}
