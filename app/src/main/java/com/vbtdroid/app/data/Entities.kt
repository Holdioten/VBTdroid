package com.vbtdroid.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

@Entity(tableName = "sessions")
data class SessionEntity(
    @androidx.room.PrimaryKey val sessionId: String = UUID.randomUUID().toString(),
    val startedAtEpochMs: Long = System.currentTimeMillis(),
    val title: String? = null,
    val lift: String = "SQUAT",
    val variant: String = "DEFAULT",
    val notes: String? = null,
)

@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["lift", "createdAtEpochMs"]),
        Index(value = ["lift", "variant", "createdAtEpochMs"]),
    ],
)
data class SetEntity(
    @androidx.room.PrimaryKey val setId: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val lift: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val metricsJson: String,
    val qualityJson: String,
    val variant: String = "DEFAULT",
    val loadKg: Double? = null,
    val plateDiameterMm: Double = 450.0,
    val cameraAngleDeg: Double = 90.0,
    val cameraLevel: Boolean = true,
    val videoUri: String = "",
    val mcvAvgMps: Double? = null,
    val exportJson: String = "",
)

@Entity(
    tableName = "reps",
    foreignKeys = [
        ForeignKey(
            entity = SetEntity::class,
            parentColumns = ["setId"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["setId", "repIndex"], unique = true)],
)
data class RepEntity(
    @androidx.room.PrimaryKey val repId: String = UUID.randomUUID().toString(),
    val setId: String,
    val repIndex: Int,
    val metricsJson: String,
    val qualityJson: String,
    val startSec: Double = 0.0,
    val endSec: Double = 0.0,
)

@Entity(
    tableName = "phases",
    foreignKeys = [
        ForeignKey(
            entity = RepEntity::class,
            parentColumns = ["repId"],
            childColumns = ["repId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["repId", "phaseIndex"], unique = true)],
)
data class PhaseEntity(
    @androidx.room.PrimaryKey val phaseId: String = UUID.randomUUID().toString(),
    val repId: String,
    val phaseIndex: Int,
    val type: String,
    val startSec: Double,
    val endSec: Double,
    val manual: Boolean,
)

@Entity(tableName = "lift_profiles", primaryKeys = ["lift", "variant"])
data class LiftProfileEntity(
    val lift: String,
    val variant: String,
    val v1rmMps: Double,
    val v1rmSource: String,
    val slope: Double?,
    val intercept: Double?,
    val r2: Double?,
    val estimated1RmKg: Double?,
    val pointCount: Int,
    val unavailableReason: String?,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
