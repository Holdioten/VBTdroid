package com.vbtdroid.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * One-shot staggered reveal (spec §1.8): fade in and rise 12dp, 40ms apart, first five items only.
 * Skipped entirely when the system animator duration scale is 0.
 */
@Composable
fun Modifier.reveal(index: Int): Modifier {
    val context = LocalContext.current
    val animatorOff = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val progress = remember { Animatable(if (animatorOff || index >= 5) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!animatorOff && index < 5) {
            delay(index * 40L)
            progress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        }
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 12.dp.toPx()
    }
}
