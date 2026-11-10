package com.vbtdroid.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalVbtColors = staticCompositionLocalOf { VbtPalettes.LightRed.toVbtColors() }

private fun schemeFor(palette: VbtPalette): ColorScheme {
    val base = if (palette.light) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primaryContainer,
        onPrimaryContainer = palette.primary,
        secondary = palette.comparison,
        onSecondary = palette.onPrimary,
        secondaryContainer = palette.surface2,
        onSecondaryContainer = palette.onSurface,
        tertiary = palette.speed,
        onTertiary = palette.onPrimary,
        tertiaryContainer = palette.surface2,
        onTertiaryContainer = palette.speed,
        background = palette.bg,
        onBackground = palette.onSurface,
        surface = palette.surface0,
        onSurface = palette.onSurface,
        surfaceVariant = palette.surface2,
        onSurfaceVariant = palette.onSurfaceVariant,
        surfaceContainerLowest = palette.bg,
        surfaceContainerLow = palette.surface0,
        surfaceContainer = palette.surface1,
        surfaceContainerHigh = palette.surface2,
        surfaceContainerHighest = palette.surface2,
        outline = palette.outline,
        outlineVariant = palette.outline,
        error = palette.error,
        onError = palette.onPrimary,
        errorContainer = palette.errorContainer,
        onErrorContainer = palette.error,
        inverseSurface = palette.onSurface,
        inverseOnSurface = palette.bg,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Applies one of the selectable palettes. Light red is the default; Arcus VBT is the original
 * dark mint theme. System bars follow the palette so the status bar stays readable.
 */
@Composable
fun VbtTheme(
    themeId: VbtThemeId = VbtThemeId.LIGHT_RED,
    content: @Composable () -> Unit,
) {
    val palette = remember(themeId) { VbtPalettes.of(themeId) }
    val scheme = remember(palette) { schemeFor(palette) }
    val colors = remember(palette) { palette.toVbtColors() }
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = palette.light
                isAppearanceLightNavigationBars = palette.light
            }
            @Suppress("DEPRECATION")
            window.statusBarColor = palette.bg.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = palette.bg.toArgb()
        }
    }
    CompositionLocalProvider(LocalVbtColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = VbtTypography,
            shapes = VbtShapes,
            content = content,
        )
    }
}

/** Access to extended tokens: `VbtTheme.colors.speed`, `VbtTheme.numbers.displayMetric`. */
object VbtTheme {
    val colors: VbtColors
        @Composable get() = LocalVbtColors.current
    val numbers: VbtNumberStyles
        get() = VbtNumbers
}
