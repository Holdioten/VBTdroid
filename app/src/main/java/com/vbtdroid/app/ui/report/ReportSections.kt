package com.vbtdroid.app.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.ui.theme.VbtSpacing
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.core.metrics.RepMetrics

/** Per-rep MCV bars; tapping a bar selects that rep. Caller supplies the card. */
@Composable
fun RepBarsContent(
    reps: List<RepMetrics>,
    selectedPosition: Int,
    onSelect: (Int) -> Unit,
) {
    val values = reps.map { it.mcvMps }
    val maxValue = values.filterNotNull().maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    val bestPosition = values.indexOfFirst { it != null && it == values.filterNotNull().maxOrNull() }
    Row(
        Modifier.fillMaxWidth().height(56.dp),
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
                                .height((40f * fraction).dp.coerceAtLeast(3.dp))
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
