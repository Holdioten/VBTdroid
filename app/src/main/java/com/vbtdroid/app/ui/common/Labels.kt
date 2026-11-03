package com.vbtdroid.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vbtdroid.app.R
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.model.LiftVariant

@Composable
internal fun liftDisplayName(lift: Lift): String = stringResource(
    when (lift) {
        Lift.SQUAT -> R.string.lift_squat
        Lift.BENCH -> R.string.lift_bench
        Lift.DEADLIFT -> R.string.lift_deadlift
    },
)

@Composable
internal fun variantDisplayName(variant: LiftVariant): String = stringResource(
    when (variant) {
        LiftVariant.HIGH_BAR -> R.string.variant_high_bar
        LiftVariant.LOW_BAR -> R.string.variant_low_bar
        LiftVariant.CONVENTIONAL -> R.string.variant_conventional
        LiftVariant.SUMO -> R.string.variant_sumo
        LiftVariant.PAUSED -> R.string.variant_paused
        LiftVariant.TOUCH_AND_GO -> R.string.variant_touch_and_go
        LiftVariant.DEFAULT -> R.string.variant_default
    },
)
