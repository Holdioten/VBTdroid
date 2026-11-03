package com.vbtdroid.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Dark-only palette from docs/ui-redesign-spec.md §1.2.
val Bg = Color(0xFF0A0E11)
val Surface0 = Color(0xFF10161B)
val Surface1 = Color(0xFF161E25)
val Surface2 = Color(0xFF1D2730)
val Outline = Color(0xFF2B3742)
val OnSurface = Color(0xFFE9EEF1)
val OnSurfaceVariant = Color(0xFF93A1AC)
val Primary = Color(0xFF3CE0B4)
val OnPrimary = Color(0xFF003827)
val PrimaryContainer = Color(0xFF0F3A30)
val Speed = Color(0xFFFFC247)
val Comparison = Color(0xFFFF7A3D)
val Skeleton = Color(0xFFF5F8FA)
val PhaseEccentric = Color(0xFF6AA7FF)
val PhasePause = Color(0xFF8C97A3)
val PhaseConcentric = Color(0xFF3CE0B4)
val PhaseLockout = Color(0xFFA6F0DC)
val ErrorColor = Color(0xFFFF6B6B)
val ErrorContainer = Color(0xFF3A1717)
val Warning = Color(0xFFFFC247)
val ConfHigh = Color(0xFF3CE0B4)
val ConfMid = Color(0xFFFFC247)
val ConfLow = Color(0xFFFF6B6B)

/** Extended tokens that Material3 has no slot for (speed, comparison, phases, confidence...). */
@Immutable
data class VbtColors(
    val speed: Color = Speed,
    val comparison: Color = Comparison,
    val skeleton: Color = Skeleton,
    val phaseEccentric: Color = PhaseEccentric,
    val phasePause: Color = PhasePause,
    val phaseConcentric: Color = PhaseConcentric,
    val phaseLockout: Color = PhaseLockout,
    val warning: Color = Warning,
    val confHigh: Color = ConfHigh,
    val confMid: Color = ConfMid,
    val confLow: Color = ConfLow,
    val surface0: Color = Surface0,
    val surface2: Color = Surface2,
    val outline: Color = Outline,
    val onSurfaceVariant: Color = OnSurfaceVariant,
    val primaryContainer: Color = PrimaryContainer,
    val errorContainer: Color = ErrorContainer,
)
