package com.vbtdroid.app.ui.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.ui.components.BannerTone
import com.vbtdroid.app.ui.components.MessageBanner
import com.vbtdroid.app.ui.theme.MinTouchTarget
import com.vbtdroid.app.ui.theme.VbtSpacing
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.app.overlay.VideoOverlayExporter
import kotlin.math.roundToInt

/** Export bottom sheet (spec §4.7). Export launchers stay in ReportScreen; this only renders them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    onDismiss: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onExportOverlay: () -> Unit,
    overlayExporting: Boolean,
    overlayProgress: Float,
    exportSucceeded: Boolean?,
    overlaySucceeded: Boolean?,
    overlayError: String?,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = VbtSpacing.l).padding(bottom = VbtSpacing.xl)) {
            Text(stringResource(R.string.report_export_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.report_export_hint),
                style = MaterialTheme.typography.bodySmall,
                color = VbtTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(top = VbtSpacing.xs, bottom = VbtSpacing.s),
            )
            ExportRow(
                title = stringResource(R.string.report_export_json),
                subtitle = "vbtdroid-set.json",
                enabled = !overlayExporting,
                onClick = onExportJson,
            )
            ExportRow(
                title = stringResource(R.string.report_export_csv),
                subtitle = "vbtdroid-set.csv",
                enabled = !overlayExporting,
                onClick = onExportCsv,
            )
            if (overlayExporting) {
                Column(Modifier.fillMaxWidth().padding(horizontal = VbtSpacing.l, vertical = VbtSpacing.s), verticalArrangement = Arrangement.spacedBy(VbtSpacing.xs)) {
                    Text(
                        stringResource(R.string.report_export_overlay_progress, (overlayProgress * 100f).roundToInt()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = { overlayProgress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            } else {
                ExportRow(
                    title = stringResource(R.string.report_export_overlay_video),
                    subtitle = "vbtdroid-overlay.mp4",
                    enabled = true,
                    onClick = onExportOverlay,
                )
            }

            exportSucceeded?.let { succeeded ->
                ResultLine(
                    if (succeeded) R.string.report_export_succeeded else R.string.report_export_failed,
                    succeeded,
                )
            }
            overlaySucceeded?.let { succeeded ->
                if (succeeded) {
                    ResultLine(R.string.report_export_overlay_succeeded, true)
                } else if (overlayError == VideoOverlayExporter.ERROR_NO_OBSERVATIONS) {
                    MessageBanner(stringResource(R.string.report_export_overlay_no_data))
                } else {
                    MessageBanner(stringResource(R.string.report_export_overlay_failed, overlayError ?: ""))
                }
            }
        }
    }
}

@Composable
private fun ExportRow(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                title,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else VbtTheme.colors.onSurfaceVariant,
            )
        },
        supportingContent = {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = VbtTheme.colors.onSurfaceVariant)
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun ResultLine(textRes: Int, succeeded: Boolean) {
    MessageBanner(
        text = stringResource(textRes),
        tone = if (succeeded) BannerTone.SUCCESS else BannerTone.ERROR,
    )
}
