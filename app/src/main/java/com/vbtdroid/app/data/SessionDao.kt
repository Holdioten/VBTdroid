package com.vbtdroid.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSet(set: SetEntity): Long

    @Update
    suspend fun updateSet(set: SetEntity): Int

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReps(reps: List<RepEntity>)

    @Query("SELECT * FROM sets WHERE lift = :lift ORDER BY createdAtEpochMs DESC")
    suspend fun setsForLift(lift: String): List<SetEntity>

    @Query("SELECT * FROM reps WHERE setId = :setId ORDER BY repIndex")
    suspend fun repsForSet(setId: String): List<RepEntity>
}
