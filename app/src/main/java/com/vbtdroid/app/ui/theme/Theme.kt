package com.vbtdroid.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue

val LocalVbtColors = staticCompositionLocalOf { VbtColors() }

private val VbtDarkScheme: ColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = Primary,
    secondary = Comparison,
    onSecondary = Bg,
    secondaryContainer = Surface2,
    onSecondaryContainer = OnSurface,
    tertiary = Speed,
    onTertiary = Bg,
    tertiaryContainer = Surface2,
    onTertiaryContainer = Speed,
    background = Bg,
    onBackground = OnSurface,
    surface = Bg,
    onSurface = OnSurface,
    surfaceVariant = Surface2,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = Bg,
    surfaceContainerLow = Surface0,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface2,
    outline = Outline,
    outlineVariant = Outline,
    error = ErrorColor,
    onError = Bg,
    errorContainer = ErrorContainer,
    onErrorContainer = ErrorColor,
    inverseSurface = OnSurface,
    inverseOnSurface = Bg,
)

/** Single dark theme (spec §1.2: v1 has no light theme). */
@Composable
fun VbtTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalVbtColors provides VbtColors()) {
        MaterialTheme(
            colorScheme = VbtDarkScheme,
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
