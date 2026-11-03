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

    /** Speed readout text: #FFC247. */
    const val SPEED = 0xFFFFC247L

    /** HUD and angle bubbles: #0A0E11 at 72%. */
    const val BUBBLE = 0xB80A0E11L

    /** Speed capsule: #14110A at 78%. */
    const val SPEED_BUBBLE = 0xC714110AL
}
