package com.vbtdroid.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.ui.theme.MinTouchTarget
import com.vbtdroid.app.ui.theme.ShapePill
import com.vbtdroid.app.ui.theme.VbtSpacing
import com.vbtdroid.app.ui.theme.VbtTheme

/** Section container: opaque surface1, 1dp outline at 60%, no elevation (spec §1.6–1.7). */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    padding: androidx.compose.ui.unit.Dp = VbtSpacing.l,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, VbtTheme.colors.outline.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(VbtSpacing.s), content = content)
    }
}

/** Group title. Use `titleMedium` with optional small caps-style eyebrow above. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** Compact metric cell: small label + headline number + optional unit. */
@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    SectionCard(modifier = modifier, padding = VbtSpacing.m) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = VbtTheme.colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(VbtSpacing.xs)) {
            Text(
                text = value,
                style = VbtTheme.numbers.headlineMetric,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = VbtTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

enum class ChipTone { NEUTRAL, PRIMARY, WARNING, ERROR }

/** 28dp pill chip used for status and metadata (spec §3.3). */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.NEUTRAL,
    maxWidth: androidx.compose.ui.unit.Dp? = null,
) {
    val colors = VbtTheme.colors
    val (container, content, border) = when (tone) {
        ChipTone.NEUTRAL -> Triple(MaterialTheme.colorScheme.surfaceContainerHigh, colors.onSurfaceVariant, null)
        ChipTone.PRIMARY -> Triple(colors.primaryContainer, MaterialTheme.colorScheme.primary, null)
        ChipTone.WARNING -> Triple(MaterialTheme.colorScheme.surfaceContainerHigh, colors.warning, colors.warning)
        ChipTone.ERROR -> Triple(colors.errorContainer, MaterialTheme.colorScheme.error, null)
    }
    Surface(
        modifier = modifier
            .height(28.dp)
            .let { if (maxWidth != null) it.widthIn(max = maxWidth) else it },
        shape = ShapePill,
        color = container,
        contentColor = content,
        border = border?.let { BorderStroke(1.dp, it.copy(alpha = 0.7f)) },
    ) {
        Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Single-choice segmented control (spec §3.3). Selected segment uses primaryContainer + primary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedPicker(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                enabled = enabled,
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.primary,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = VbtTheme.colors.onSurfaceVariant,
                    inactiveBorderColor = VbtTheme.colors.outline,
                ),
                label = {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

/** Full-row switch: the whole row toggles, not just the thumb (spec §3.3). */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = VbtSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VbtSpacing.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else VbtTheme.colors.onSurfaceVariant,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VbtTheme.colors.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

enum class BannerTone { ERROR, WARNING, SUCCESS }

/** Status banner: error uses errorContainer fill; warning/success are text+icon only. */
@Composable
fun MessageBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.ERROR,
) {
    val colors = VbtTheme.colors
    val (container, content) = when (tone) {
        BannerTone.ERROR -> colors.errorContainer to MaterialTheme.colorScheme.error
        BannerTone.WARNING -> MaterialTheme.colorScheme.surfaceContainerHigh to colors.warning
        BannerTone.SUCCESS -> colors.primaryContainer to MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = container,
    ) {
        Row(
            Modifier.padding(horizontal = VbtSpacing.m, vertical = VbtSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s),
        ) {
            Icon(
                imageVector = if (tone == BannerTone.SUCCESS) Icons.Filled.Check else Icons.Filled.Warning,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp),
            )
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = content)
        }
    }
}

/** Shared bottom action bar: tonal elevation is the only elevation allowed (spec §1.6). */
@Composable
fun PrimaryActionBar(
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Column {
            HorizontalDivider(color = VbtTheme.colors.outline)
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = VbtSpacing.l, vertical = VbtSpacing.m),
                verticalArrangement = Arrangement.spacedBy(VbtSpacing.s),
            ) {
                if (hint != null) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = VbtTheme.colors.onSurfaceVariant,
                    )
                }
                content()
            }
        }
    }
}

/**
 * Primary button colours. Disabled state keeps readable text (onSurfaceVariant on surface2) instead of
 * the Material default 38% grey-on-grey.
 */
@Composable
fun primaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    disabledContentColor = VbtTheme.colors.onSurfaceVariant,
)

/** Secondary outlined colours with a readable disabled state. */
@Composable
fun secondaryOutlinedColors() = ButtonDefaults.outlinedButtonColors(
    contentColor = MaterialTheme.colorScheme.onSurface,
    disabledContentColor = VbtTheme.colors.onSurfaceVariant,
)

/** Primary full-width CTA (52dp). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.small,
        colors = primaryButtonColors(),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String? = null,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(VbtSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VbtSpacing.s),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (body != null) {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = VbtTheme.colors.onSurfaceVariant)
        }
        OutlinedButton(
            onClick = onAction,
            modifier = Modifier.defaultMinSize(minHeight = MinTouchTarget),
        ) {
            Text(actionLabel)
        }
    }
}

/** Quality meter: 0..1 bar with trailing numeric value. */
@Composable
fun MeterRow(
    label: String,
    fraction: Float,
    valueText: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VbtSpacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = VbtTheme.colors.onSurfaceVariant)
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

/** Confidence tier colour (spec §1.2 confHigh/confMid/confLow). */
@Composable
fun confidenceColor(value: Double?): Color = when {
    value == null -> VbtTheme.colors.onSurfaceVariant
    value >= 0.80 -> VbtTheme.colors.confHigh
    value >= 0.50 -> VbtTheme.colors.confMid
    else -> VbtTheme.colors.confLow
}
