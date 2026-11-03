package com.vbtdroid.core.phases

import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.model.Lift
import kotlin.math.max

enum class PhaseType { ECCENTRIC, PAUSE, CONCENTRIC, LOCKOUT }

data class Phase(
    val type: PhaseType,
    val startSec: Double,
    val endSec: Double,
    val manual: Boolean = false,
)

data class RepSlice(
    val index: Int,
    val startSec: Double,
    val endSec: Double,
    val phases: List<Phase>,
)

data class PhaseConfig(
    /** Velocity threshold; lower values split normal tracking jitter into fake phases. */
    val vEps: Double = 0.05,
    val minPhaseSec: Double = 0.12,
    val lockoutSec: Double = 0.10,
    val minRepSec: Double = 0.45,
    val minConcentricSec: Double = 0.15,
    val minRomM: Double = 0.10,
    val mergePauseSec: Double = 0.25,
)

data class PhaseSegmentation(
    val reps: List<RepSlice>,
    val warnings: List<String>,
)

/**
 * Splits a set into reps from vertical bar velocity.
 *
 * A formal rep only needs an eccentric and a concentric phase. A lockout is optional: many sets
 * are continuous and never stop at the top. Missing lockout therefore produces a
 * [NO_LOCKOUT] quality hint, not a rejected rep. Candidates that cannot stand as a rep (missing
 * eccentric/concentric, too short, or too little vertical travel) are dropped and counted as
 * [INCOMPLETE_REP].
 */
object PhaseSegmenter {
    const val NO_LOCKOUT = "NO_LOCKOUT"
    const val INCOMPLETE_REP = "INCOMPLETE_REP"

    /** Legacy name kept for persisted reports created before reps stopped requiring a lockout. */
    const val INCOMPLETE_REP_NO_LOCKOUT = "INCOMPLETE_REP_NO_LOCKOUT"

    fun segment(
        samples: List<MotionSample>,
        lift: Lift,
        config: PhaseConfig = PhaseConfig(),
    ): List<RepSlice> = segmentDetailed(samples, lift, config).reps

    fun segmentDetailed(
        samples: List<MotionSample>,
        lift: Lift,
        config: PhaseConfig = PhaseConfig(),
    ): PhaseSegmentation {
        require(config.vEps.isFinite() && config.vEps >= 0.0) { "vEps must be finite and non-negative" }
        require(config.minPhaseSec.isFinite() && config.minPhaseSec >= 0.0) {
            "minPhaseSec must be finite and non-negative"
        }
        require(config.lockoutSec.isFinite() && config.lockoutSec >= 0.0) {
            "lockoutSec must be finite and non-negative"
        }
        require(config.minRepSec.isFinite() && config.minRepSec >= 0.0) {
            "minRepSec must be finite and non-negative"
        }
        require(config.minConcentricSec.isFinite() && config.minConcentricSec >= 0.0) {
            "minConcentricSec must be finite and non-negative"
        }
        require(config.minRomM.isFinite() && config.minRomM >= 0.0) {
            "minRomM must be finite and non-negative"
        }
        require(config.mergePauseSec.isFinite() && config.mergePauseSec >= 0.0) {
            "mergePauseSec must be finite and non-negative"
        }
        if (samples.isEmpty()) return PhaseSegmentation(emptyList(), emptyList())
        for (index in samples.indices) {
            require(samples[index].tSec.isFinite() && samples[index].vyMps.isFinite()) {
                "sample timestamps and vertical velocities must be finite"
            }
            if (index > 0) {
                require(samples[index].tSec > samples[index - 1].tSec) {
                    "sample timestamps must be strictly increasing"
                }
            }
        }

        // Pauses between two runs in the same direction are tracking noise, not rep boundaries.
        // Merge them before detecting lockouts so a short concentric hiccup cannot end a rep.
        val sameDirectionMerged = mergePausesBetweenSameDirection(
            classify(samples, config.vEps),
            config.mergePauseSec,
        )
        val runsWithLockouts = detectLockouts(sameDirectionMerged, config.lockoutSec)
        val runs = mergeShortRuns(runsWithLockouts, config.minPhaseSec)
        val result = when (lift) {
            Lift.SQUAT, Lift.BENCH -> segmentEccentricLifts(runs, samples, config)
            Lift.DEADLIFT -> segmentDeadlifts(runs, samples, config)
        }
        // A noisy track can split one rep into two adjacent candidates separated by a tiny gap
        // with no real travel. Merge only those; reps separated by actual displacement stay split.
        val reps = mergeNoiseFragments(result.reps, samples, config)
        val warnings = buildList {
            if (result.incompleteCount > 0) add(INCOMPLETE_REP)
            if (reps.any { rep -> rep.phases.none { it.type == PhaseType.LOCKOUT } }) add(NO_LOCKOUT)
        }
        return PhaseSegmentation(reps, warnings)
    }

    /** Duration-margin confidence for phase review; manual boundaries always require review. */
    fun confidence(rep: RepSlice, config: PhaseConfig = PhaseConfig()): Double {
        if (rep.phases.any { it.manual }) return 0.7
        val margin = rep.phases.minOfOrNull { phase ->
            val required = when (phase.type) {
                PhaseType.LOCKOUT -> config.lockoutSec
                PhaseType.CONCENTRIC -> max(config.minPhaseSec, config.minConcentricSec)
                else -> config.minPhaseSec
            }
            if (required <= 0.0) 1.0 else (phase.endSec - phase.startSec) / required
        } ?: 0.0
        return margin.coerceIn(0.0, 1.0)
    }

    private data class Run(
        val type: PhaseType,
        val startSec: Double,
        val endSec: Double,
    ) {
        val durationSec: Double get() = endSec - startSec
    }

    private data class MutableSegmentation(
        val reps: List<RepSlice>,
        val incompleteCount: Int,
        val noLockoutCount: Int,
    )

    private fun classify(samples: List<MotionSample>, vEps: Double): List<Run> {
        val runs = mutableListOf<Run>()
        val exitEps = vEps * 0.5
        var type = phaseFor(samples.first().vyMps, vEps)
        var start = samples.first().tSec

        for (index in 1 until samples.size) {
            val vyMps = samples[index].vyMps
            val nextType = when (type) {
                PhaseType.ECCENTRIC -> if (vyMps >= -exitEps) phaseFor(vyMps, vEps) else PhaseType.ECCENTRIC
                PhaseType.CONCENTRIC -> if (vyMps <= exitEps) phaseFor(vyMps, vEps) else PhaseType.CONCENTRIC
                PhaseType.PAUSE, PhaseType.LOCKOUT -> phaseFor(vyMps, vEps)
            }
            if (nextType != type) {
                runs += Run(type, start, samples[index].tSec)
                type = nextType
                start = samples[index].tSec
            }
        }
        runs += Run(type, start, samples.last().tSec)
        return runs
    }

    private fun phaseFor(vyMps: Double, vEps: Double): PhaseType = when {
        vyMps < -vEps -> PhaseType.ECCENTRIC
        vyMps > vEps -> PhaseType.CONCENTRIC
        else -> PhaseType.PAUSE
    }

    /** Collapses a short pause that sits between two runs moving in the same direction. */
    private fun mergePausesBetweenSameDirection(source: List<Run>, mergePauseSec: Double): List<Run> {
        val runs = source.toMutableList()
        var index = 0
        while (index < runs.size) {
            val run = runs[index]
            val previous = runs.getOrNull(index - 1)
            val next = runs.getOrNull(index + 1)
            if (run.type == PhaseType.PAUSE &&
                run.durationSec + 1e-9 <= mergePauseSec &&
                previous != null && next != null &&
                previous.type == next.type &&
                (previous.type == PhaseType.ECCENTRIC || previous.type == PhaseType.CONCENTRIC)
            ) {
                runs[index - 1] = Run(previous.type, previous.startSec, next.endSec)
                runs.removeAt(index + 1)
                runs.removeAt(index)
                index = max(0, index - 1)
            } else {
                index++
            }
        }
        return runs
    }

    private fun detectLockouts(source: List<Run>, lockoutSec: Double): List<Run> = source.mapIndexed { index, run ->
        if (run.type == PhaseType.PAUSE && index > 0 && source[index - 1].type == PhaseType.CONCENTRIC &&
            run.durationSec + 1e-9 >= lockoutSec
        ) {
            run.copy(type = PhaseType.LOCKOUT)
        } else {
            run
        }
    }

    /** Uses the generic minimum for ordinary phases, never for a detected lockout. */
    private fun mergeShortRuns(source: List<Run>, minPhaseSec: Double): List<Run> {
        val runs = source.toMutableList()
        if (runs.size == 1 && runs.single().durationSec < minPhaseSec &&
            runs.single().type != PhaseType.LOCKOUT
        ) return emptyList()
        var index = 0
        while (index < runs.size && runs.size > 1) {
            if (runs[index].type == PhaseType.LOCKOUT || runs[index].durationSec + 1e-9 >= minPhaseSec) {
                index++
                continue
            }

            val previous = runs.getOrNull(index - 1)
            val next = runs.getOrNull(index + 1)
            if (previous == null && next == null) {
                runs.removeAt(index)
                continue
            }
            val targetIndex = when {
                previous == null -> index + 1
                next == null -> index - 1
                previous.type == next.type -> index - 1
                previous.durationSec >= next.durationSec -> index - 1
                else -> index + 1
            }
            val run = runs[index]
            val target = runs[targetIndex]
            val merged = Run(
                type = target.type,
                startSec = minOf(run.startSec, target.startSec),
                endSec = maxOf(run.endSec, target.endSec),
            )
            val first = minOf(index, targetIndex)
            val second = maxOf(index, targetIndex)
            runs.removeAt(second)
            runs[first] = merged
            var adjacent = 1
            while (adjacent < runs.size) {
                if (runs[adjacent - 1].type == runs[adjacent].type) {
                    val left = runs[adjacent - 1]
                    val right = runs.removeAt(adjacent)
                    runs[adjacent - 1] = left.copy(endSec = right.endSec)
                } else {
                    adjacent++
                }
            }
            index = maxOf(0, first - 1)
        }
        return if (runs.size == 1 && runs.single().durationSec < minPhaseSec &&
            runs.single().type != PhaseType.LOCKOUT
        ) emptyList() else runs
    }

    private fun segmentEccentricLifts(
        runs: List<Run>,
        samples: List<MotionSample>,
        config: PhaseConfig,
    ): MutableSegmentation {
        val reps = mutableListOf<RepSlice>()
        var phases = mutableListOf<Phase>()
        var incomplete = 0
        var noLockout = 0

        fun finish() {
            if (phases.isEmpty()) return
            val candidate = validRep(phases, samples, config, requireEccentric = true)
            if (candidate == null) {
                incomplete++
            } else {
                reps += candidate.copy(index = reps.size + 1)
                if (candidate.phases.none { it.type == PhaseType.LOCKOUT }) noLockout++
            }
            phases = mutableListOf()
        }

        for (run in runs) {
            when (run.type) {
                PhaseType.ECCENTRIC -> {
                    if (phases.any { it.type == PhaseType.CONCENTRIC }) finish()
                    phases += run.toPhase()
                }
                PhaseType.CONCENTRIC -> {
                    if (phases.any { it.type == PhaseType.ECCENTRIC }) phases += run.toPhase()
                }
                PhaseType.PAUSE -> if (phases.isNotEmpty()) phases += run.toPhase()
                PhaseType.LOCKOUT -> {
                    if (phases.any { it.type == PhaseType.CONCENTRIC }) {
                        appendLockout(phases, run, config.lockoutSec)
                        finish()
                    }
                }
            }
        }
        finish()
        return MutableSegmentation(reps, incomplete, noLockout)
    }

    /** Deadlift starts with the pull; the descent is the separator between reps. */
    private fun segmentDeadlifts(
        runs: List<Run>,
        samples: List<MotionSample>,
        config: PhaseConfig,
    ): MutableSegmentation {
        val reps = mutableListOf<RepSlice>()
        var phases = mutableListOf<Phase>()
        var incomplete = 0
        var noLockout = 0

        fun finish() {
            if (phases.isEmpty()) return
            val candidate = validRep(phases, samples, config, requireEccentric = false)
            if (candidate == null) {
                incomplete++
            } else {
                reps += candidate.copy(index = reps.size + 1)
                if (candidate.phases.none { it.type == PhaseType.LOCKOUT }) noLockout++
            }
            phases = mutableListOf()
        }

        for (run in runs) {
            when (run.type) {
                PhaseType.CONCENTRIC -> {
                    if (phases.any { it.type == PhaseType.CONCENTRIC }) finish()
                    phases += run.toPhase()
                }
                PhaseType.ECCENTRIC -> if (phases.any { it.type == PhaseType.CONCENTRIC }) finish()
                PhaseType.PAUSE -> if (phases.isNotEmpty()) phases += run.toPhase()
                PhaseType.LOCKOUT -> {
                    if (phases.any { it.type == PhaseType.CONCENTRIC }) {
                        appendLockout(phases, run, config.lockoutSec)
                        finish()
                    }
                }
            }
        }
        finish()
        return MutableSegmentation(reps, incomplete, noLockout)
    }

    private fun appendLockout(phases: MutableList<Phase>, run: Run, lockoutSec: Double) {
        val phase = run.toPhase()
        val capped = phase.copy(
            endSec = minOf(phase.endSec, phase.startSec + lockoutSec),
        )
        if (capped.endSec > capped.startSec) phases += capped
    }

    private fun validRep(
        phases: List<Phase>,
        samples: List<MotionSample>,
        config: PhaseConfig,
        requireEccentric: Boolean,
    ): RepSlice? {
        if ((requireEccentric && phases.none { it.type == PhaseType.ECCENTRIC }) ||
            phases.none { it.type == PhaseType.CONCENTRIC }
        ) {
            return null
        }
        val ordered = phases.filter { it.endSec > it.startSec }.sortedBy { it.startSec }
        if (ordered.isEmpty()) return null
        val startSec = ordered.first().startSec
        val endSec = ordered.last().endSec
        if (endSec - startSec + 1e-9 < config.minRepSec) return null

        val concentricSec = ordered
            .filter { it.type == PhaseType.CONCENTRIC }
            .sumOf { it.endSec - it.startSec }
        if (concentricSec + 1e-9 < config.minConcentricSec) return null

        val repSamples = samples.filter { it.tSec >= startSec && it.tSec <= endSec }
        if (repSamples.size < 2) return null
        val romM = repSamples.maxOf { it.yM } - repSamples.minOf { it.yM }
        if (romM + 1e-9 < config.minRomM) return null

        if (!hasTurnaround(repSamples, ordered, config)) return null

        return RepSlice(
            index = 0,
            startSec = startSec,
            endSec = endSec,
            phases = ordered,
        )
    }

    /**
     * A real rep reverses direction: vertical acceleration changes sign around the concentric
     * velocity peak, or the bar changes direction outright. Synthetic or degraded tracks without
     * acceleration data fall back to a clear velocity peak followed by deceleration.
     */
    private fun hasTurnaround(
        repSamples: List<MotionSample>,
        phases: List<Phase>,
        config: PhaseConfig,
    ): Boolean {
        val concentric = phases.filter { it.type == PhaseType.CONCENTRIC }
        val concentricSamples = repSamples.filter { sample ->
            concentric.any { sample.tSec >= it.startSec - 1e-9 && sample.tSec <= it.endSec + 1e-9 }
        }
        val window = if (concentricSamples.size >= 2) concentricSamples else repSamples
        val peakIndex = window.indices.maxByOrNull { window[it].vyMps } ?: return false

        if (hasAccelerationSignChange(window, peakIndex)) return true
        if (hasVelocitySignChange(repSamples)) return true

        val peakVy = window[peakIndex].vyMps
        if (peakVy + 1e-9 >= config.vEps) {
            val peakY = window[peakIndex].yM
            val decelerated = window.drop(peakIndex + 1).any { sample ->
                sample.vyMps <= peakVy * 0.5 + 1e-9 &&
                    kotlin.math.abs(sample.yM - peakY) + 1e-9 >= config.minRomM * 0.5
            }
            if (decelerated) return true
        }
        return false
    }

    private fun hasAccelerationSignChange(window: List<MotionSample>, peakIndex: Int): Boolean {
        val from = (peakIndex - 2).coerceAtLeast(0)
        val to = (peakIndex + 2).coerceAtMost(window.lastIndex)
        var previous = 0.0
        var havePrevious = false
        for (index in from..to) {
            val ay = window[index].ayMps2?.takeIf { it.isFinite() } ?: continue
            if (kotlin.math.abs(ay) < 1e-9) continue
            if (havePrevious && previous * ay < 0.0) return true
            previous = ay
            havePrevious = true
        }
        return false
    }

    private fun hasVelocitySignChange(samples: List<MotionSample>): Boolean {
        var sign = 0.0
        for (sample in samples) {
            val vy = sample.vyMps
            if (kotlin.math.abs(vy) < 1e-9) continue
            if (sign != 0.0 && sign * vy < 0.0) return true
            sign = vy
        }
        return false
    }

    /** Merges adjacent candidates only when the gap is short and contains no meaningful travel. */
    private fun mergeNoiseFragments(
        reps: List<RepSlice>,
        samples: List<MotionSample>,
        config: PhaseConfig,
    ): List<RepSlice> {
        if (reps.size < 2) return reps.mapIndexed { index, rep -> rep.copy(index = index + 1) }
        val merged = mutableListOf<RepSlice>()
        for (rep in reps) {
            val previous = merged.lastOrNull()
            val gapSec = if (previous == null) Double.POSITIVE_INFINITY else rep.startSec - previous.endSec
            if (previous != null &&
                gapSec > 1e-9 &&
                gapSec + 1e-9 < config.mergePauseSec &&
                previous.phases.none { it.type == PhaseType.LOCKOUT } &&
                gapHasNoDisplacement(previous.endSec, rep.startSec, samples, config)
            ) {
                val phases = (previous.phases + rep.phases)
                    .filter { it.endSec > it.startSec }
                    .sortedBy { it.startSec }
                merged[merged.lastIndex] = previous.copy(
                    endSec = maxOf(previous.endSec, rep.endSec),
                    phases = phases,
                )
            } else {
                merged += rep
            }
        }
        return merged.mapIndexed { index, rep -> rep.copy(index = index + 1) }
    }

    private fun gapHasNoDisplacement(
        gapStartSec: Double,
        gapEndSec: Double,
        samples: List<MotionSample>,
        config: PhaseConfig,
    ): Boolean {
        val gapSamples = samples.filter { it.tSec >= gapStartSec && it.tSec <= gapEndSec }
        if (gapSamples.size < 2) return false
        val travel = gapSamples.maxOf { it.yM } - gapSamples.minOf { it.yM }
        return travel + 1e-9 < config.minRomM * 0.5
    }

    private fun Run.toPhase() = Phase(type, startSec, endSec)
}
