package com.vbtdroid.app.ui.report

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.core.metrics.RepMetrics
import com.vbtdroid.core.model.MetricId

@Composable
fun RepTable(reps: List<RepMetrics>, modifier: Modifier = Modifier) {
    val columns = listOf<Pair<MetricId, (RepMetrics) -> Double?>>(
        MetricId.MEAN_CONCENTRIC_VELOCITY to { it.mcvMps },
        MetricId.MEAN_PROPULSIVE_VELOCITY to { it.mpvMps },
        MetricId.PEAK_VELOCITY to { it.peakVelocityMps },
        MetricId.ROM_TOTAL to { it.romTotalM },
        MetricId.VELOCITY_LOSS to { it.velocityLossPct },
        MetricId.STICKING_POINT to { it.stickingPointPct },
        MetricId.MEAN_FORCE to { it.meanForceN },
        MetricId.MEAN_POWER to { it.meanPowerW },
    )
    Column(modifier.horizontalScroll(rememberScrollState())) {
        Row {
            TableCell(stringResource(R.string.report_rep), isHeader = true, width = 56)
            columns.forEach { (metric, _) ->
                TableCell(metricDisplayName(metric), isHeader = true)
            }
        }
        reps.forEach { rep ->
            Row {
                TableCell(rep.repIndex.toString(), width = 56)
                columns.forEach { (metric, value) ->
                    TableCell(formatMetricValue(value(rep), metric))
                }
            }
        }
    }
}

@Composable
private fun TableCell(text: String, isHeader: Boolean = false, width: Int = 112) {
    Text(
        text = text,
        modifier = Modifier.width(width.dp).padding(horizontal = 6.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
internal fun metricDisplayName(metric: MetricId): String = stringResource(
    when (metric) {
        MetricId.MEAN_CONCENTRIC_VELOCITY -> R.string.metric_mcv
        MetricId.MEAN_PROPULSIVE_VELOCITY -> R.string.metric_mpv
        MetricId.PEAK_VELOCITY -> R.string.metric_peak_velocity
        MetricId.ROM_TOTAL -> R.string.metric_rom_total
        MetricId.VELOCITY_LOSS -> R.string.metric_velocity_loss
        MetricId.SET_VELOCITY_LOSS -> R.string.metric_set_velocity_loss
        MetricId.STICKING_POINT -> R.string.metric_sticking_point
        MetricId.MEAN_FORCE -> R.string.metric_mean_force
        MetricId.MEAN_POWER -> R.string.metric_mean_power
        MetricId.VOLUME_LOAD -> R.string.metric_volume_load
        MetricId.WORK -> R.string.metric_work
        MetricId.HIP_ANGLE -> R.string.metric_hip_angle
        MetricId.KNEE_ANGLE -> R.string.metric_knee_angle
        MetricId.ANKLE_ANGLE -> R.string.metric_ankle_angle
        MetricId.TRUNK_ANGLE -> R.string.metric_trunk_angle
        MetricId.SHOULDER_ANGLE -> R.string.metric_shoulder_angle
        MetricId.ELBOW_ANGLE -> R.string.metric_elbow_angle
        MetricId.WRIST_ANGLE -> R.string.metric_wrist_angle
        else -> error("No report display mapping for ${metric.id}")
    },
)

@Composable
internal fun formatMetricValue(value: Double?, metric: MetricId): String {
    if (value == null) return stringResource(R.string.report_value_missing)
    val decimals = when (metric) {
        MetricId.MEAN_FORCE, MetricId.MEAN_POWER, MetricId.WORK, MetricId.VOLUME_LOAD -> 1
        MetricId.VELOCITY_LOSS, MetricId.SET_VELOCITY_LOSS, MetricId.STICKING_POINT -> 1
        MetricId.HIP_ANGLE, MetricId.KNEE_ANGLE, MetricId.ANKLE_ANGLE, MetricId.TRUNK_ANGLE,
        MetricId.SHOULDER_ANGLE, MetricId.ELBOW_ANGLE, MetricId.WRIST_ANGLE -> 1
        else -> 3
    }
    return "%.${decimals}f".format(value)
}
