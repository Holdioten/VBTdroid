package com.vbtdroid.app.ui.report

import com.vbtdroid.app.analysis.FrameAngles
import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.metrics.RepMetrics
import com.vbtdroid.core.phases.PhaseType
import com.vbtdroid.core.phases.RepSlice
import kotlin.math.abs

internal enum class KeyFrameKind { START, BOTTOM_OR_TOUCH, STICKING, LOCKOUT }
internal data class KeyFrameAngles(val kind: KeyFrameKind, val angles: FrameAngles)

internal fun selectKeyFrameAngles(
    rep: RepMetrics,
    slice: RepSlice,
    motion: List<MotionSample>,
    angles: List<FrameAngles>,
): List<KeyFrameAngles> {
    if (angles.isEmpty()) return emptyList()
    val repMotion = motion.filter { it.tSec >= slice.startSec && it.tSec <= slice.endSec }
    val selected = LinkedHashMap<KeyFrameKind, FrameAngles>()
    selected[KeyFrameKind.START] = angles.minByOrNull { it.tSec }!!
    repMotion.minByOrNull { it.yM }?.let { bottom ->
        selected[KeyFrameKind.BOTTOM_OR_TOUCH] = angles.minByOrNull { abs(it.tSec - bottom.tSec) }!!
    }
    rep.stickingPointPct?.let { stickingPct ->
        val concentric = slice.phases.filter { it.type == PhaseType.CONCENTRIC }
        val concentricMotion = repMotion.filter { sample ->
            concentric.any { sample.tSec >= it.startSec && sample.tSec <= it.endSec }
        }
        val first = concentricMotion.firstOrNull()
        if (first != null) {
            val targetY = first.yM + rep.romConcentricM * stickingPct / 100.0
            val sticking = concentricMotion.minByOrNull { abs(it.yM - targetY) }
            if (sticking != null) {
                selected[KeyFrameKind.STICKING] = angles.minByOrNull { abs(it.tSec - sticking.tSec) }!!
            }
        }
    }
    selected[KeyFrameKind.LOCKOUT] = angles.maxByOrNull { it.tSec }!!
    return selected.map { KeyFrameAngles(it.key, it.value) }
}
