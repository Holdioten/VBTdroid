package com.vbtdroid.core.phases

import com.vbtdroid.core.metrics.MotionSample

/** One consecutive run of samples that share a phase type (null when no phase covers them). */
data class TrailRun(val type: PhaseType?, val samples: List<MotionSample>)

/**
 * Time → rep/phase lookups for playback. Pure JVM so the overlay renderer and the UI share it.
 *
 * A rep covers `[startSec, endSec)`; the last rep also includes its end instant. Times in gaps
 * between reps, before the first rep, or after the last rep map to null ("no rep").
 */
object RepTimeline {
    fun currentRepAt(reps: List<RepSlice>, tSec: Double): RepSlice? {
        if (!tSec.isFinite() || reps.isEmpty()) return null
        reps.firstOrNull { tSec >= it.startSec && tSec < it.endSec }?.let { return it }
        return reps.last().takeIf { tSec == it.endSec }
    }

    /**
     * Rep whose trail belongs at [tSec]. Inside a rep it is that rep; in the interval after rep i
     * ends and before rep i+1 starts it stays rep i, so its full trail remains on screen. The trail
     * switches only when the next rep starts. After the last rep its trail also stays.
     */
    fun trailRepAt(reps: List<RepSlice>, tSec: Double): RepSlice? {
        if (!tSec.isFinite() || reps.isEmpty()) return null
        currentRepAt(reps, tSec)?.let { return it }
        return reps.filter { tSec >= it.endSec }.maxByOrNull { it.endSec }
    }

    fun phaseAt(rep: RepSlice, tSec: Double): Phase? {
        if (!tSec.isFinite()) return null
        rep.phases.firstOrNull { tSec >= it.startSec && tSec < it.endSec }?.let { return it }
        return rep.phases.lastOrNull()?.takeIf { tSec == it.endSec }
    }

    /**
     * Samples of [rep] from its start up to [upToSec] (inclusive), split into runs of one phase type.
     * Each run starts with the last sample of the previous run so the drawn path has no gaps.
     * Samples outside the rep are never included, so a trail cannot carry over from another rep.
     */
    fun trailRuns(samples: List<MotionSample>, rep: RepSlice, upToSec: Double): List<TrailRun> {
        if (samples.isEmpty() || !upToSec.isFinite()) return emptyList()
        val limit = minOf(upToSec, rep.endSec)
        val inRep = samples.filter { it.tSec >= rep.startSec && it.tSec <= limit }
        if (inRep.isEmpty()) return emptyList()
        val runs = mutableListOf<TrailRun>()
        var currentType: PhaseType? = null
        var current = mutableListOf<MotionSample>()
        for (sample in inRep) {
            val type = phaseAt(rep, sample.tSec)?.type
            if (current.isEmpty()) {
                currentType = type
                current.add(sample)
            } else if (type == currentType) {
                current.add(sample)
            } else {
                runs.add(TrailRun(currentType, current))
                val bridge = current.last()
                current = mutableListOf(bridge, sample)
                currentType = type
            }
        }
        if (current.isNotEmpty()) runs.add(TrailRun(currentType, current))
        return runs
    }
}
