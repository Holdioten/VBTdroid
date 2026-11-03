package com.vbtdroid.app.ui.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.ui.components.MetricTile
import com.vbtdroid.app.ui.components.SectionCard
import com.vbtdroid.app.ui.components.SectionLabel
import com.vbtdroid.app.ui.theme.VbtSpacing
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.core.metrics.RepMetrics
import com.vbtdroid.core.model.MetricId

/** MCV hero (spec §4.2 item 3): the only 64sp number on the report screen. */
@Composable
fun MetricHero(
    mcv: Double?,
    bestMcv: Double?,
    velocityLossPct: Double?,
    repCount: Int,
    loadKg: Double?,
    modifier: Modifier = Modifier,
) {
    val missing = stringResource(R.string.report_value_missing)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, VbtTheme.colors.outline.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(VbtSpacing.l), verticalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
            Text(
                text = stringResource(R.string.metric_mcv),
                style = MaterialTheme.typography.bodyMedium,
                color = VbtTheme.colors.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
                Text(
                    text = formatMcv(mcv) ?: missing,
                    style = VbtTheme.numbers.displayMetric,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.unit_mps),
                    style = MaterialTheme.typography.titleMedium,
                    color = VbtTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VbtSpacing.m)) {
                MiniStat(stringResource(R.string.report_mini_best_mcv), formatMcv(bestMcv) ?: missing, Modifier.weight(1f))
                MiniStat(
                    stringResource(R.string.report_mini_vl),
                    formatMetricValue(velocityLossPct, MetricId.SET_VELOCITY_LOSS),
                    Modifier.weight(1f),
                )
                MiniStat(stringResource(R.string.report_mini_reps), repCount.toString(), Modifier.weight(1f))
            }
            if (loadKg != null) {
                Text(
                    stringResource(R.string.report_external_load_assumption),
                    style = MaterialTheme.typography.labelSmall,
                    color = VbtTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = VbtTheme.colors.onSurfaceVariant)
        Text(value, style = VbtTheme.numbers.headlineMetric, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** 2×2 summary grid (spec §4.2 item 5). */
@Composable
fun SetMetricGrid(
    repCount: Int,
    volumeKg: Double?,
    workJ: Double?,
    bestMcv: Double?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
            MetricTile(
                label = stringResource(R.string.report_total_reps),
                value = repCount.toString(),
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = stringResource(R.string.metric_volume_load),
                value = formatMetricValue(volumeKg, MetricId.VOLUME_LOAD),
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
            MetricTile(
                label = stringResource(R.string.metric_work),
                value = formatMetricValue(workJ, MetricId.WORK),
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = stringResource(R.string.report_best_of_short),
                value = formatMcv(bestMcv) ?: stringResource(R.string.report_value_missing),
                modifier = Modifier.weight(1f),
                valueColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Per-rep MCV bars; tapping a bar selects that rep (spec §4.2 item 6). */
@Composable
fun RepMcvBars(
    reps: List<RepMetrics>,
    selectedPosition: Int,
    onSelect: (Int) -> Unit,
) {
    val values = reps.map { it.mcvMps }
    val maxValue = values.filterNotNull().maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    val bestPosition = values.indexOfFirst { it != null && it == values.filterNotNull().maxOrNull() }
    SectionCard {
        SectionLabel(stringResource(R.string.report_per_rep_mcv))
        Row(
            Modifier.fillMaxWidth().height(128.dp),
            horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s),
            verticalAlignment = Alignment.Bottom,
        ) {
            reps.forEachIndexed { position, rep ->
                val selected = position == selectedPosition
                val fraction = ((rep.mcvMps ?: 0.0) / maxValue).toFloat().coerceIn(0f, 1f)
                val barColor = if (position == bestPosition) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                }
                val description = stringResource(
                    R.string.report_rep_bar_description,
                    rep.repIndex,
                    formatMcv(rep.mcvMps) ?: stringResource(R.string.report_value_missing),
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(position) }
                        .semantics { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((96f * fraction).dp.coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .background(barColor)
                                .then(
                                    if (selected) {
                                        Modifier.border(1.5.dp, VbtTheme.colors.skeleton, RoundedCornerShape(6.dp))
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                    Text(
                        text = rep.repIndex.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else VbtTheme.colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Horizontal rep chips; selected chip uses primaryContainer (spec §4.2 item 7). */
@Composable
fun RepSelectorRow(
    count: Int,
    selectedPosition: Int,
    onSelect: (Int) -> Unit,
    repIndexOf: (Int) -> Int,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s),
    ) {
        (0 until count).forEach { position ->
            FilterChip(
                selected = position == selectedPosition,
                onClick = { onSelect(position) },
                label = { Text(stringResource(R.string.report_rep_number, repIndexOf(position))) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}
