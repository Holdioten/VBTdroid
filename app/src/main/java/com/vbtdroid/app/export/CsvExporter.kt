package com.vbtdroid.app.export

import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.core.metrics.RepMetrics

object CsvExporter {
    private const val HEADER = "rep,mcv,mpv,pv,rom,vl,sticking,meanForce,meanPower"

    fun export(result: AnalysisResult): String = buildString {
        appendLine(HEADER)
        result.reps.forEach { rep ->
            appendLine(rep.toCsvRow())
        }
    }.trimEnd('\n', '\r')

    private fun RepMetrics.toCsvRow(): String = listOf(
        repIndex.toString(),
        mcvMps.csvValue(),
        mpvMps.csvValue(),
        peakVelocityMps.csvValue(),
        romTotalM.csvValue(),
        velocityLossPct.csvValue(),
        stickingPointPct.csvValue(),
        meanForceN.csvValue(),
        meanPowerW.csvValue(),
    ).joinToString(",")

    private fun Double?.csvValue(): String = this?.toString().orEmpty()
}
