package com.vbtdroid.app.ui.import

/** UI plate diameter is centimetres; analysis and storage stay in metres. */
internal const val CM_PER_M = 100.0

/** Converts a valid centimetre input to metres; null for blank, non-finite, or non-positive input. */
internal fun plateDiameterMeters(cm: Double?): Double? =
    cm?.takeIf { it.isFinite() && it > 0.0 }?.div(CM_PER_M)
