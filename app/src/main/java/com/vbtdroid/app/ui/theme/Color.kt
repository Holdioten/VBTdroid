package com.vbtdroid.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Selectable app themes. Light red is the default; Arcus VBT keeps the original mint palette. */
enum class VbtThemeId {
    LIGHT_RED,
    DARK_RED,
    ARCUS_VBT,
}

@Immutable
data class VbtPalette(
    val id: VbtThemeId,
    val light: Boolean,
    val bg: Color,
    val surface0: Color,
    val surface1: Color,
    val surface2: Color,
    val outline: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val speed: Color,
    val comparison: Color,
    val skeleton: Color,
    val phaseEccentric: Color,
    val phasePause: Color,
    val phaseConcentric: Color,
    val phaseLockout: Color,
    val error: Color,
    val errorContainer: Color,
    val warning: Color,
    val confHigh: Color,
    val confMid: Color,
    val confLow: Color,
)

object VbtPalettes {
    val LightRed = VbtPalette(
        id = VbtThemeId.LIGHT_RED,
        light = true,
        bg = Color(0xFFF7F8FA),
        surface0 = Color(0xFFFFFFFF),
        surface1 = Color(0xFFF1F4F6),
        surface2 = Color(0xFFE7ECEF),
        outline = Color(0xFFD3DBE0),
        onSurface = Color(0xFF12171B),
        onSurfaceVariant = Color(0xFF5A6772),
        primary = Color(0xFFD5202A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFBE7E8),
        speed = Color(0xFFB26B00),
        comparison = Color(0xFFC2410C),
        skeleton = Color(0xFFE2E7EA),
        phaseEccentric = Color(0xFF2563EB),
        phasePause = Color(0xFF6B7280),
        phaseConcentric = Color(0xFF0F8A6A),
        phaseLockout = Color(0xFF34A853),
        error = Color(0xFFB42318),
        errorContainer = Color(0xFFFDECEA),
        warning = Color(0xFFB54708),
        confHigh = Color(0xFF0F8A6A),
        confMid = Color(0xFFB54708),
        confLow = Color(0xFFB42318),
    )

    val DarkRed = VbtPalette(
        id = VbtThemeId.DARK_RED,
        light = false,
        bg = Color(0xFF0A0E11),
        surface0 = Color(0xFF10161B),
        surface1 = Color(0xFF161E25),
        surface2 = Color(0xFF1D2730),
        outline = Color(0xFF2B3742),
        onSurface = Color(0xFFE9EEF1),
        onSurfaceVariant = Color(0xFF93A1AC),
        primary = Color(0xFFE5484D),
        onPrimary = Color(0xFF1A0708),
        primaryContainer = Color(0xFF3A1518),
        speed = Color(0xFFFFC247),
        comparison = Color(0xFFFF7A3D),
        skeleton = Color(0xFFF5F8FA),
        phaseEccentric = Color(0xFF6AA7FF),
        phasePause = Color(0xFF8C97A3),
        phaseConcentric = Color(0xFF3CE0B4),
        phaseLockout = Color(0xFFA6F0DC),
        error = Color(0xFFFF8A8F),
        errorContainer = Color(0xFF3A1717),
        warning = Color(0xFFFFC247),
        confHigh = Color(0xFF3CE0B4),
        confMid = Color(0xFFFFC247),
        confLow = Color(0xFFFF8A8F),
    )

    /** Original VBTdroid palette: dark surfaces with mint data accents. */
    val ArcusVbt = VbtPalette(
        id = VbtThemeId.ARCUS_VBT,
        light = false,
        bg = Color(0xFF0A0E11),
        surface0 = Color(0xFF10161B),
        surface1 = Color(0xFF161E25),
        surface2 = Color(0xFF1D2730),
        outline = Color(0xFF2B3742),
        onSurface = Color(0xFFE9EEF1),
        onSurfaceVariant = Color(0xFF93A1AC),
        primary = Color(0xFF3CE0B4),
        onPrimary = Color(0xFF003827),
        primaryContainer = Color(0xFF0F3A30),
        speed = Color(0xFFFFC247),
        comparison = Color(0xFFFF7A3D),
        skeleton = Color(0xFFF5F8FA),
        phaseEccentric = Color(0xFF6AA7FF),
        phasePause = Color(0xFF8C97A3),
        phaseConcentric = Color(0xFF3CE0B4),
        phaseLockout = Color(0xFFA6F0DC),
        error = Color(0xFFFF6B6B),
        errorContainer = Color(0xFF3A1717),
        warning = Color(0xFFFFC247),
        confHigh = Color(0xFF3CE0B4),
        confMid = Color(0xFFFFC247),
        confLow = Color(0xFFFF6B6B),
    )

    fun of(id: VbtThemeId): VbtPalette = when (id) {
        VbtThemeId.LIGHT_RED -> LightRed
        VbtThemeId.DARK_RED -> DarkRed
        VbtThemeId.ARCUS_VBT -> ArcusVbt
    }
}

/** Extended tokens that Material3 has no slot for (speed, comparison, phases, confidence...). */
@Immutable
data class VbtColors(
    val speed: Color,
    val comparison: Color,
    val skeleton: Color,
    val phaseEccentric: Color,
    val phasePause: Color,
    val phaseConcentric: Color,
    val phaseLockout: Color,
    val warning: Color,
    val confHigh: Color,
    val confMid: Color,
    val confLow: Color,
    val surface0: Color,
    val surface2: Color,
    val outline: Color,
    val onSurfaceVariant: Color,
    val primaryContainer: Color,
    val errorContainer: Color,
)

fun VbtPalette.toVbtColors(): VbtColors = VbtColors(
    speed = speed,
    comparison = comparison,
    skeleton = skeleton,
    phaseEccentric = phaseEccentric,
    phasePause = phasePause,
    phaseConcentric = phaseConcentric,
    phaseLockout = phaseLockout,
    warning = warning,
    confHigh = confHigh,
    confMid = confMid,
    confLow = confLow,
    surface0 = surface0,
    surface2 = surface2,
    outline = outline,
    onSurfaceVariant = onSurfaceVariant,
    primaryContainer = primaryContainer,
    errorContainer = errorContainer,
)
