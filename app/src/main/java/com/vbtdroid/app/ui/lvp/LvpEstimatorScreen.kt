package com.vbtdroid.app.ui.lvp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.data.OneRmEstimateEntity
import com.vbtdroid.app.data.SetEntity
import com.vbtdroid.app.ui.components.SegmentedPicker
import com.vbtdroid.app.ui.theme.MinTouchTarget
import com.vbtdroid.app.ui.theme.VbtSpacing
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.core.io.JsonExport
import com.vbtdroid.core.metrics.Lvp
import com.vbtdroid.core.metrics.LvpEstimate
import com.vbtdroid.core.metrics.LvpPoint
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.model.LiftVariant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class EstimateOutcome(
    val estimate: LvpEstimate,
    val maxLoadKg: Double,
    val points: List<LvpPoint>,
    val saved: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LvpEstimatorScreen(
    history: List<SetEntity>,
    estimates: List<OneRmEstimateEntity>,
    onSave: (OneRmEstimateEntity) -> Unit,
    onBack: () -> Unit,
) {
    var lift by remember { mutableStateOf(Lift.SQUAT) }
    var variant by remember { mutableStateOf(defaultVariant(lift)) }
    var selectedLoads by remember { mutableStateOf<Set<Double>>(emptySet()) }
    var outcome by remember { mutableStateOf<EstimateOutcome?>(null) }

    val eligible = history.filter {
        it.lift == lift.name &&
            it.variant == variant.name &&
            it.loadKg != null &&
            it.mcvAvgMps != null
    }.sortedWith(
        compareByDescending<SetEntity> { it.loadKg ?: 0.0 }
            .thenByDescending { it.createdAtEpochMs },
    )
    val loadGroups = remember(eligible) {
        eligible.groupBy { it.loadKg!! }.toSortedMap(compareByDescending { it })
    }
    val selectedSets = eligible.filter { (it.loadKg ?: 0.0) in selectedLoads }
    val canEstimate = selectedLoads.size >= 3

    fun resetSelection() {
        selectedLoads = emptySet()
        outcome = null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.report_back),
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(R.string.lvp_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = VbtSpacing.l),
        ) {
            Spacer(Modifier.height(VbtSpacing.s))
            SegmentedPicker(
                options = Lift.entries,
                selected = lift,
                label = { liftLabel(it) },
                onSelect = {
                    lift = it
                    variant = defaultVariant(it)
                    resetSelection()
                },
            )
            if (lift != Lift.BENCH) {
                Spacer(Modifier.height(VbtSpacing.s))
                SegmentedPicker(
                    options = variantsFor(lift),
                    selected = variant,
                    label = { variantLabel(it) },
                    onSelect = {
                        variant = it
                        resetSelection()
                    },
                )
            }
            Spacer(Modifier.height(VbtSpacing.m))
            Text(
                stringResource(R.string.lvp_select_hint),
                style = MaterialTheme.typography.bodySmall,
                color = VbtTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(VbtSpacing.s))
            if (eligible.isEmpty()) {
                Text(
                    stringResource(R.string.lvp_no_sets),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VbtTheme.colors.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(loadGroups.entries.toList(), key = { it.key }) { entry ->
                        val load = entry.key
                        val sets = entry.value
                        val checked = load in selectedLoads
                        val avgMcv = sets.mapNotNull { it.mcvAvgMps }.average()
                        val latest = sets.maxByOrNull { it.createdAtEpochMs }
                        val reps = latest?.let {
                            runCatching { JsonExport.decode(it.exportJson).reps.size }.getOrNull()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedLoads = if (checked) selectedLoads - load else selectedLoads + load
                                    outcome = null
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selectedLoads = if (checked) selectedLoads - load else selectedLoads + load
                                    outcome = null
                                },
                            )
                            Column(Modifier.width(96.dp)) {
                                Text(
                                    latest?.let { formatDate(it.createdAtEpochMs) } ?: "-",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VbtTheme.colors.onSurfaceVariant,
                                )
                                Text(
                                    "${sets.size} 组 · ${reps ?: "-"} 次",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VbtTheme.colors.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${formatKg(load)} kg",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(84.dp),
                            )
                            Text(
                                String.format(Locale.US, "MCV %.3f", avgMcv),
                                style = MaterialTheme.typography.bodySmall,
                                color = VbtTheme.colors.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(color = VbtTheme.colors.outline.copy(alpha = 0.4f))
                    }
                }
            }
            Text(
                stringResource(R.string.lvp_selected, selectedLoads.size, selectedSets.size),
                style = MaterialTheme.typography.bodySmall,
                color = VbtTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(VbtSpacing.s))
            val current = outcome
            if (current != null) {
                ResultCard(current)
                Spacer(Modifier.height(VbtSpacing.s))
            }
            Button(
                onClick = {
                    val points = selectedSets
                        .groupBy { it.loadKg!! }
                        .map { (load, sets) -> LvpPoint(load, sets.mapNotNull { it.mcvAvgMps }.average()) }
                        .sortedBy { it.loadKg }
                    val estimate = Lvp.estimate(
                        points = points,
                        lift = lift,
                        v1rmMps = Lvp.defaultV1rmMps(lift),
                    )
                    val maxLoad = points.maxOfOrNull { it.loadKg } ?: 0.0
                    val model = estimate.model
                    val valid = model != null && model.estimated1RmKg >= maxLoad
                    if (valid) {
                        onSave(
                            OneRmEstimateEntity(
                                lift = lift.name,
                                variant = variant.name,
                                estimateKg = model!!.estimated1RmKg,
                                mvtMps = estimate.v1rmMps,
                                slope = model.slope,
                                intercept = model.intercept,
                                r2 = model.r2,
                                pointCount = points.size,
                                setIds = selectedSets.joinToString(",") { it.setId },
                            ),
                        )
                    }
                    outcome = EstimateOutcome(estimate, maxLoad, points, saved = valid)
                },
                enabled = canEstimate,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouchTarget),
            ) {
                Text(stringResource(R.string.lvp_estimate))
            }
            Spacer(Modifier.height(VbtSpacing.m))
        }
    }
}

@Composable
private fun ResultCard(outcome: EstimateOutcome) {
    val model = outcome.estimate.model
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(VbtSpacing.m),
    ) {
        val text = when {
            model == null -> stringResource(
                R.string.lvp_unavailable,
                outcome.estimate.unavailableReason.orEmpty(),
            )
            model.estimated1RmKg < outcome.maxLoadKg -> stringResource(
                R.string.lvp_below_lifted,
                outcome.maxLoadKg,
            )
            else -> null
        }
        if (text != null) {
            Text(text, color = MaterialTheme.colorScheme.error)
        } else {
            val m = model!!
            Text(
                stringResource(R.string.lvp_reference),
                style = MaterialTheme.typography.bodySmall,
                color = VbtTheme.colors.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    String.format(Locale.US, "%.1f", m.estimated1RmKg),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.unit_kg),
                    style = MaterialTheme.typography.titleMedium,
                    color = VbtTheme.colors.onSurfaceVariant,
                )
            }
            Text(
                stringResource(
                    R.string.lvp_fit,
                    outcome.estimate.v1rmMps,
                    m.r2,
                    outcome.points.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = VbtTheme.colors.onSurfaceVariant,
            )
            if (outcome.saved) {
                Text(
                    stringResource(R.string.lvp_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(VbtSpacing.s))
        outcome.points.forEach { point ->
            Text(
                String.format(Locale.US, "%.1f kg · %.3f m/s", point.loadKg, point.mcvMps),
                style = MaterialTheme.typography.bodySmall,
                color = VbtTheme.colors.onSurfaceVariant,
            )
        }
    }
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(epochMs))

private fun formatKg(value: Double): String =
    String.format(Locale.US, "%.1f", value)

private fun liftLabel(lift: Lift): String = when (lift) {
    Lift.SQUAT -> "深蹲"
    Lift.BENCH -> "卧推"
    Lift.DEADLIFT -> "硬拉"
}

private fun defaultVariant(lift: Lift): LiftVariant = when (lift) {
    Lift.SQUAT -> LiftVariant.HIGH_BAR
    Lift.BENCH -> LiftVariant.TOUCH_AND_GO
    Lift.DEADLIFT -> LiftVariant.CONVENTIONAL
}

private fun variantsFor(lift: Lift): List<LiftVariant> = when (lift) {
    Lift.SQUAT -> listOf(LiftVariant.HIGH_BAR, LiftVariant.LOW_BAR)
    Lift.BENCH -> listOf(LiftVariant.TOUCH_AND_GO)
    Lift.DEADLIFT -> listOf(LiftVariant.CONVENTIONAL, LiftVariant.SUMO)
}

private fun variantLabel(variant: LiftVariant): String = when (variant) {
    LiftVariant.HIGH_BAR -> "高杠"
    LiftVariant.LOW_BAR -> "低杠"
    LiftVariant.TOUCH_AND_GO -> "触胸即起"
    LiftVariant.PAUSED -> "暂停"
    LiftVariant.CONVENTIONAL -> "传统"
    LiftVariant.SUMO -> "相扑"
    else -> variant.name
}
