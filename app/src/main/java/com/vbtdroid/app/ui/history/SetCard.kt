package com.vbtdroid.app.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.data.SetEntity
import com.vbtdroid.app.ui.report.formatKgValue
import com.vbtdroid.app.ui.report.formatMcv
import com.vbtdroid.app.ui.report.formatMetricValue
import com.vbtdroid.app.ui.theme.MinTouchTarget
import com.vbtdroid.app.ui.theme.VbtSpacing
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.core.io.JsonExport
import com.vbtdroid.core.model.MetricId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** List card for one saved set. The whole card opens the detail (spec §5.1). */
@Composable
fun SetCard(set: SetEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val missing = stringResource(R.string.report_value_missing)
    val export = remember(set.setId) { runCatching { JsonExport.decode(set.exportJson) }.getOrNull() }
    val velocityLoss = export?.summary?.velocityLossPct
    val repCount = export?.reps?.size
    val pattern = stringResource(R.string.history_date_format)
    val date = remember(set.createdAtEpochMs, pattern) {
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(set.createdAtEpochMs))
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, VbtTheme.colors.outline.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(VbtSpacing.l), verticalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.history_set_identity, liftLabel(set.lift), variantLabel(set.variant)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    date,
                    style = MaterialTheme.typography.bodySmall,
                    color = VbtTheme.colors.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VbtSpacing.m),
            ) {
                MetricCell(
                    label = stringResource(R.string.history_metric_load),
                    value = "${formatKgValue(set.loadKg) ?: missing} kg",
                    modifier = Modifier.weight(1f),
                )
                MetricCell(
                    label = stringResource(R.string.history_metric_reps),
                    value = repCount?.toString() ?: missing,
                    modifier = Modifier.weight(1f),
                )
                MetricCell(
                    label = stringResource(R.string.history_metric_mcv),
                    value = formatMcv(set.mcvAvgMps) ?: missing,
                    unit = stringResource(R.string.unit_mps),
                    highlight = true,
                    modifier = Modifier.weight(1f),
                )
            }
            if (velocityLoss != null) {
                Text(
                    "${stringResource(R.string.report_mini_vl)} ${formatMetricValue(velocityLoss, MetricId.SET_VELOCITY_LOSS)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VbtTheme.colors.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.history_open_detail),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    highlight: Boolean = false,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = VbtTheme.colors.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = if (highlight) VbtTheme.numbers.displayMetricSm else MaterialTheme.typography.titleMedium,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (unit != null) {
                Spacer(Modifier.width(2.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelLarge,
                    color = VbtTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = if (highlight) 6.dp else 0.dp),
                )
            }
        }
    }
}
