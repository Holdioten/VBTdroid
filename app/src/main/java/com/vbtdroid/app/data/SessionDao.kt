package com.vbtdroid.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: SetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReps(reps: List<RepEntity>)

    @Query("SELECT * FROM sets WHERE lift = :lift ORDER BY createdAtEpochMs DESC")
    suspend fun setsForLift(lift: String): List<SetEntity>
}
