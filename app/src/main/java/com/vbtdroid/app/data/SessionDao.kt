package com.vbtdroid.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

data class LoadVelocityRow(val loadKg: Double, val mcvAvgMps: Double)

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSet(set: SetEntity): Long

    @Update
    suspend fun updateSet(set: SetEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: SessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReps(reps: List<RepEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPhases(phases: List<PhaseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLiftProfile(profile: LiftProfileEntity)

    @Query("SELECT * FROM sets WHERE lift = :lift ORDER BY createdAtEpochMs DESC")
    suspend fun setsForLift(lift: String): List<SetEntity>

    @Query("SELECT * FROM sets WHERE lift = :lift AND variant = :variant ORDER BY createdAtEpochMs DESC")
    suspend fun setsForLiftVariant(lift: String, variant: String): List<SetEntity>

    @Query(
        "SELECT * FROM sets WHERE lift = :lift AND variant = :variant AND loadKg = :loadKg " +
            "ORDER BY createdAtEpochMs DESC",
    )
    suspend fun setsForLiftVariantLoad(lift: String, variant: String, loadKg: Double): List<SetEntity>

    @Query("SELECT * FROM sets ORDER BY createdAtEpochMs DESC")
    suspend fun history(): List<SetEntity>

    @Query("SELECT * FROM sets WHERE setId = :setId")
    suspend fun setById(setId: String): SetEntity?

    @Query("SELECT * FROM reps WHERE setId = :setId ORDER BY repIndex")
    suspend fun repsForSet(setId: String): List<RepEntity>

    @Query("DELETE FROM reps WHERE setId = :setId")
    suspend fun deleteRepsForSet(setId: String)

    @Query("SELECT * FROM phases WHERE repId = :repId ORDER BY phaseIndex")
    suspend fun phasesForRep(repId: String): List<PhaseEntity>

    @Query(
        "SELECT loadKg, mcvAvgMps FROM sets " +
            "WHERE lift = :lift AND variant = :variant AND loadKg IS NOT NULL AND mcvAvgMps IS NOT NULL " +
            "ORDER BY createdAtEpochMs ASC",
    )
    suspend fun loadVelocityPoints(lift: String, variant: String): List<LoadVelocityRow>

    @Query("SELECT * FROM lift_profiles WHERE lift = :lift AND variant = :variant")
    suspend fun liftProfile(lift: String, variant: String): LiftProfileEntity?
}
