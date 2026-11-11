package com.vbtdroid.app.overlay

import com.vbtdroid.app.ui.theme.VbtThemeId

/**
 * Overlay colours (ARGB, Long) for the burned-in video cards.
 *
 * The palette follows the app theme selected when the analysis starts: light red uses white
 * translucent cards, dark red and Arcus VBT use dark cards. Card alpha is 70% for the light
 * palette and 75% for the dark ones; the video should stay visible behind the data.
 */
object OverlayStyle {
    /** Skeleton and angle labels: #F5F8FA. */
    var SKELETON = 0xFFF5F8FAL
        private set

    /** Skeleton line at 85% alpha. */
    var SKELETON_LINE = 0xD9F5F8FAL
        private set

    /** Barbell trail, curve and hero numbers. */
    var TRAIL = 0xFF3CE0B4L
        private set

    /** Eccentric (lowering) trail. */
    var ECCENTRIC = 0xFF6AA7FFL
        private set

    /** Pause trail, drawn dim. */
    var PAUSE = 0xFF8C97A3L
        private set

    /** Lockout trail, drawn dim. */
    var LOCKOUT = 0xFFA6F0DCL
        private set

    /** Sticking region band and dashed lines. */
    var STICKING = 0xFFFF9F43L
        private set

    /** Speed readout text. */
    var SPEED = 0xFFFFC247L
        private set

    /** HUD and angle bubbles. */
    var BUBBLE = 0xB80A0E11L
        private set

    /** Speed capsule. */
    var SPEED_BUBBLE = 0xC714110AL
        private set

    /** Text drawn on a bubble. */
    var BUBBLE_TEXT = 0xFFE9EEF1L
        private set

    /** Data card background. */
    var CARD_BACKGROUND = 0xD10A0E11L
        private set

    /** Data card hairline border. */
    var CARD_BORDER = 0x802B3742L
        private set

    /** Card labels and secondary values. */
    var CARD_MUTED = 0xFF93A1ACL
        private set

    /** Card primary text. */
    var CARD_TEXT = 0xFFE9EEF1L
        private set

    /** Selects the card palette for one analysis run. */
    fun apply(theme: VbtThemeId) {
        when (theme) {
            VbtThemeId.LIGHT_RED -> {
                SKELETON = 0xFFF5F8FAL
                SKELETON_LINE = 0xD9F5F8FAL
                TRAIL = 0xFFD5202AL
                ECCENTRIC = 0xFF2563EBL
                PAUSE = 0xFF6B7280L
                LOCKOUT = 0xFF34A853L
                STICKING = 0xFFF57C00L
                SPEED = 0xFFD5202AL
                BUBBLE = 0xB3FFFFFFL
                SPEED_BUBBLE = 0xC7FFFFFFL
                BUBBLE_TEXT = 0xFF12171BL
                CARD_BACKGROUND = 0xB3FFFFFFL
                CARD_BORDER = 0x4DD3DBE0L
                CARD_MUTED = 0xFF5A6772L
                CARD_TEXT = 0xFF12171BL
            }
            VbtThemeId.DARK_RED -> {
                SKELETON = 0xFFF5F8FAL
                SKELETON_LINE = 0xD9F5F8FAL
                TRAIL = 0xFFE5484DL
                ECCENTRIC = 0xFF6AA7FFL
                PAUSE = 0xFF8C97A3L
                LOCKOUT = 0xFFF2B8BBL
                STICKING = 0xFFFF9F43L
                SPEED = 0xFFFFC247L
                BUBBLE = 0xB80A0E11L
                SPEED_BUBBLE = 0xC70A0E11L
                BUBBLE_TEXT = 0xFFE9EEF1L
                CARD_BACKGROUND = 0xBF0A0E11L
                CARD_BORDER = 0x802B3742L
                CARD_MUTED = 0xFF93A1ACL
                CARD_TEXT = 0xFFE9EEF1L
            }
            VbtThemeId.ARCUS_VBT -> {
                SKELETON = 0xFFF5F8FAL
                SKELETON_LINE = 0xD9F5F8FAL
                TRAIL = 0xFF3CE0B4L
                ECCENTRIC = 0xFF6AA7FFL
                PAUSE = 0xFF8C97A3L
                LOCKOUT = 0xFFA6F0DCL
                STICKING = 0xFFFF9F43L
                SPEED = 0xFFFFC247L
                BUBBLE = 0xB80A0E11L
                SPEED_BUBBLE = 0xC70A0E11L
                BUBBLE_TEXT = 0xFFE9EEF1L
                CARD_BACKGROUND = 0xBF0A0E11L
                CARD_BORDER = 0x802B3742L
                CARD_MUTED = 0xFF93A1ACL
                CARD_TEXT = 0xFFE9EEF1L
            }
        }
    }
}
