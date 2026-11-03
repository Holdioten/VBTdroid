package com.vbtdroid.app.export

import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.core.metrics.RepMetrics

object CsvExporter {
    private const val HEADER = "rep,rep_duration_s,mcv_mps,mpv_mps,peak_velocity_mps," +
        "rom_total_m,rom_concentric_m,velocity_loss_pct,sticking_point_pct,mean_force_n,mean_power_w," +
        "peak_force_n,peak_power_w,peak_acceleration_mps2,peak_deceleration_mps2,work_j"

    fun export(result: AnalysisResult): String = buildString {
        appendLine(HEADER)
        result.reps.forEach { rep ->
            appendLine(rep.toCsvRow())
        }
    }.trimEnd('\n', '\r')

    private fun RepMetrics.toCsvRow(): String = listOf(
        repIndex.toString(),
        durationS.csvValue(),
        mcvMps.csvValue(),
        mpvMps.csvValue(),
        peakVelocityMps.csvValue(),
        romTotalM.csvValue(),
        romConcentricM.csvValue(),
        velocityLossPct.csvValue(),
        stickingPointPct.csvValue(),
        meanForceN.csvValue(),
        meanPowerW.csvValue(),
        peakForceN.csvValue(),
        peakPowerW.csvValue(),
        peakAccelerationMps2.csvValue(),
        peakDecelerationMps2.csvValue(),
        workJ.csvValue(),
    ).joinToString(",")

    private fun Double?.csvValue(): String = this?.toString().orEmpty()
}
