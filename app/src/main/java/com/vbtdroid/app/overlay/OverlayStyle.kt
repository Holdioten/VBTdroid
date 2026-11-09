package com.vbtdroid.app.overlay

/**
 * Overlay colours (ARGB, Long) shared with the app theme tokens in ui/theme/Color.kt.
 * Spec: docs/ui-redesign-spec.md §6.7. Keep the values in sync with the theme tokens.
 */
object OverlayStyle {
    /** Skeleton and angle labels: #F5F8FA. */
    const val SKELETON = 0xFFF5F8FAL

    /** Skeleton line at 85% alpha. */
    const val SKELETON_LINE = 0xD9F5F8FAL

    /** Barbell trail and head: primary #3CE0B4. */
    const val TRAIL = 0xFF3CE0B4L

    /** Eccentric (lowering) trail: phaseEccentric #6AA7FF. */
    const val ECCENTRIC = 0xFF6AA7FFL

    /** Pause trail: phasePause #8C97A3, drawn dim. */
    const val PAUSE = 0xFF8C97A3L

    /** Lockout trail: phaseLockout #A6F0DC, drawn dim. */
    const val LOCKOUT = 0xFFA6F0DCL

    /** Sticking region: orange #FF9F43. */
    const val STICKING = 0xFFFF9F43L

    /** Speed readout text: #FFC247. */
    const val SPEED = 0xFFFFC247L

    /** HUD and angle bubbles: #0A0E11 at 72%. */
    const val BUBBLE = 0xB80A0E11L

    /** Speed capsule: #14110A at 78%. */
    const val SPEED_BUBBLE = 0xC714110AL

    /** Data card background: #0A0E11 at 82%, matching the report GlassColor. */
    const val CARD_BACKGROUND = 0xD10A0E11L

    /** Data card hairline border: report outline #2B3742 at 50%. */
    const val CARD_BORDER = 0x802B3742L

    /** Card labels and secondary values: report onSurfaceVariant #93A1AC. */
    const val CARD_MUTED = 0xFF93A1ACL

    /** Card primary text: report onSurface #E9EEF1. */
    const val CARD_TEXT = 0xFFE9EEF1L
}
