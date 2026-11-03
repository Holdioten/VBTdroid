package com.vbtdroid.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vbtdroid.app.R

/** Digits only: Barlow Condensed (OFL), two weights. CJK text uses the system font. */
val NumberFamily = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
)

private const val TABULAR = "tnum"

/** Numeric styles from spec §1.3; use these for metric values instead of ad-hoc sizes. */
@Immutable
data class VbtNumberStyles(
    val displayMetric: TextStyle = TextStyle(
        fontFamily = NumberFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 64.sp, lineHeight = 64.sp, fontFeatureSettings = TABULAR,
    ),
    val displayMetricSm: TextStyle = TextStyle(
        fontFamily = NumberFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp, lineHeight = 44.sp, fontFeatureSettings = TABULAR,
    ),
    val headlineMetric: TextStyle = TextStyle(
        fontFamily = NumberFamily, fontWeight = FontWeight.Medium,
        fontSize = 28.sp, lineHeight = 32.sp, fontFeatureSettings = TABULAR,
    ),
)

val VbtNumbers = VbtNumberStyles()

val VbtTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
)
