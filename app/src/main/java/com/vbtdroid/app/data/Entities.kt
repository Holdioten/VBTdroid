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
    indices = [Index("sessionId"), Index(value = ["lift", "createdAtEpochMs"])],
)
data class SetEntity(
    @androidx.room.PrimaryKey val setId: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val lift: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val metricsJson: String,
    val qualityJson: String,
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
    indices = [Index("setId")],
)
data class RepEntity(
    @androidx.room.PrimaryKey val repId: String = UUID.randomUUID().toString(),
    val setId: String,
    val repIndex: Int,
    val metricsJson: String,
    val qualityJson: String,
)
