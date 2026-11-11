package com.vbtdroid.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One manually computed 1RM estimate. The user picks the sets, so the record keeps the exact
 * inputs (set ids) and the fit parameters for later review.
 */
@Entity(tableName = "one_rm_estimates")
data class OneRmEstimateEntity(
    @PrimaryKey val estimateId: String = UUID.randomUUID().toString(),
    val lift: String,
    val variant: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val estimateKg: Double,
    val mvtMps: Double,
    val slope: Double,
    val intercept: Double,
    val r2: Double,
    val pointCount: Int,
    val setIds: String,
)
