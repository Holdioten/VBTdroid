package com.vbtdroid.core.phases

import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.model.Lift
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

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
    /**
     * Minimum vertical bar travel for a rep. 0.20 m is the app default for squat/deadlift:
     * a deliberate quarter pull on the tuning videos reached ~0.12 m while real reps reached
     * ~0.40 m, so 0.10 m used to let partial pulls through.
     */
    val minRomM: Double = 0.20,
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
 * [INCOMPLETE_REP]. Once a set has several reps, candidates whose vertical travel is under half
 * the set median are dropped as partial pulls and reported as [REP_ROM_OUTLIER].
 *
 * Deadlift sets do not have a clean eccentric/concentric alternation (the pull starts from the
 * floor), so they are detected from positive vertical-velocity runs instead: each pull is one
 * contiguous `vy > vEps` run, starting at the local bottom just before the run. Squat and bench
 * keep the phase-run based detector.
 */
object PhaseSegmenter {
    const val NO_LOCKOUT = "NO_LOCKOUT"
    const val INCOMPLETE_REP = "INCOMPLETE_REP"
    /** Dropped candidate whose ROM was far below the set's median (e.g. a quarter pull). */
    const val REP_ROM_OUTLIER = "REP_ROM_OUTLIER"

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

        if (lift == Lift.DEADLIFT) {
            val detected = segmentDeadliftsByVelocity(samples, config)
            // Relative ROM gate: a deliberate quarter pull can clear the absolute floor but is far
            // below the set's typical travel, so drop it once there are enough reps for a median.
            val (finalReps, droppedRomOutliers) = dropRomOutliers(detected.reps, samples, config)
            val warnings = buildList {
                if (detected.incompleteCount > 0) add(INCOMPLETE_REP)
                if (droppedRomOutliers > 0) add(REP_ROM_OUTLIER)
                if (finalReps.any { rep -> rep.phases.none { it.type == PhaseType.LOCKOUT } }) add(NO_LOCKOUT)
            }
            return PhaseSegmentation(finalReps, warnings)
        }
        // Pauses between two runs in the same direction are tracking noise, not rep boundaries.
        // Merge them before detecting lockouts so a short concentric hiccup cannot end a rep.
        val sameDirectionMerged = mergePausesBetweenSameDirection(
            classify(samples, config.vEps),
            config.mergePauseSec,
        )
        val runsWithLockouts = detectLockouts(sameDirectionMerged, config.lockoutSec)
        val runs = mergeShortRuns(runsWithLockouts, config.minPhaseSec)
        val result = segmentEccentricLifts(runs, samples, config)
        // A noisy track can split one rep into two adjacent candidates separated by a tiny gap
        // with no real travel. Merge only those; reps separated by actual displacement stay split.
        val reps = mergeNoiseFragments(result.reps, samples, config)
        // Relative ROM gate: a deliberate quarter pull can clear the absolute floor but is far
        // below the set's typical travel, so drop it once there are enough reps for a median.
        val (finalReps, droppedRomOutliers) = dropRomOutliers(reps, samples, config)
        val warnings = buildList {
            if (result.incompleteCount > 0) add(INCOMPLETE_REP)
            if (droppedRomOutliers > 0) add(REP_ROM_OUTLIER)
            if (finalReps.any { rep -> rep.phases.none { it.type == PhaseType.LOCKOUT } }) add(NO_LOCKOUT)
        }
        // The phase-run path can collapse several cycles when the velocity track is noisy or
        // pauses between reps are misread. y extrema keep one clean top/bottom per rep, so use
        // them to recover squat/bench reps, but only when they find strictly more complete reps.
        if (lift == Lift.SQUAT || lift == Lift.BENCH) {
            val recovered = recoverRepsFromYExtrema(samples, config)
            val (finalRecovered, recoveredOutliers) = dropRomOutliers(recovered, samples, config)
            if (finalRecovered.size > finalReps.size) {
                val recoveredWarnings = buildList {
                    if (recoveredOutliers > 0) add(REP_ROM_OUTLIER)
                    if (finalRecovered.any { rep -> rep.phases.none { it.type == PhaseType.LOCKOUT } }) {
                        add(NO_LOCKOUT)
                    }
                }
                return PhaseSegmentation(finalRecovered, recoveredWarnings)
            }
        }
        return PhaseSegmentation(finalReps, warnings)
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

    /** A contiguous positive vertical-velocity run; indices point into the sample list. */
    private data class VelocityRun(
        val startIndex: Int,
        val endIndex: Int,
    )

    private data class MutableSegmentation(
        val reps: List<RepSlice>,
        val incompleteCount: Int,
        val noLockoutCount: Int,
    )

    private data class YExtremum(val index: Int, val y: Double, val isBottom: Boolean)

    /**
     * Rebuilds squat/bench reps from smoothed vertical-position extrema.
     *
     * Bar height `yM` is upward-positive throughout the app (`Calibration` negates pixel y), so a
     * squat/bench bottom is a local y minimum and a top is a local y maximum. One rep is a
     * top -> bottom -> top cycle. Extrema need at least [PhaseConfig.minRomM] of prominence and
     * same-kind extrema need at least [PhaseConfig.minRepSec] (or
     * [EXTREMA_MIN_SEPARATION_SEC]) between them. Incomplete leading/trailing edges are skipped:
     * a boundary extremum is only used when the track starts/ends at rest, and a bottom without a
     * top on both sides never becomes a rep.
     */
    private fun recoverRepsFromYExtrema(
        samples: List<MotionSample>,
        config: PhaseConfig,
    ): List<RepSlice> {
        if (samples.size < 3) return emptyList()
        val smoothed = smoothY(samples)
        val extrema = yExtrema(samples, smoothed, config.minRomM)
        if (extrema.size < 3) return emptyList()

        val alternating = filterYExtrema(extrema, config.minRomM)
        if (alternating.size < 3) return emptyList()

        val minSeparationSec = max(config.minRepSec, EXTREMA_MIN_SEPARATION_SEC)
        val reps = mutableListOf<RepSlice>()
        var lastBottomSec = Double.NEGATIVE_INFINITY
        for (position in 1 until alternating.size - 1) {
            val bottom = alternating[position]
            if (!bottom.isBottom) continue
            val topBefore = alternating[position - 1]
            val topAfter = alternating[position + 1]
            if (topBefore.isBottom || topAfter.isBottom) continue
            val bottomSec = samples[bottom.index].tSec
            if (bottomSec - lastBottomSec + 1e-9 < minSeparationSec) continue
            val rep = extremaRep(topBefore, bottom, topAfter, samples, config) ?: continue
            reps += rep
            lastBottomSec = bottomSec
        }
        return reps.mapIndexed { index, rep -> rep.copy(index = index + 1) }
    }

    private fun extremaRep(
        topBefore: YExtremum,
        bottom: YExtremum,
        topAfter: YExtremum,
        samples: List<MotionSample>,
        config: PhaseConfig,
    ): RepSlice? {
        val startIndex = topBefore.index
        val bottomIndex = bottom.index
        val endIndex = topAfter.index
        if (startIndex >= bottomIndex || bottomIndex >= endIndex) return null
        val startSec = samples[startIndex].tSec
        val endSec = samples[endIndex].tSec
        if (endSec - startSec + 1e-9 < config.minRepSec) return null

        // ROM is measured top -> bottom; the higher of the two bounding tops is the rep's top.
        val romM = max(topBefore.y, topAfter.y) - bottom.y
        if (romM + 1e-9 < config.minRomM) return null

        val repSamples = samples.subList(startIndex, endIndex + 1)
        val hasMotion = repSamples.any { abs(it.vyMps) > config.vEps }
        val bottomSec = samples[bottomIndex].tSec
        val phases = mutableListOf<Phase>()
        if (!hasMotion) {
            // Degraded velocity track: direction is still fully determined by the y extrema.
            phases += Phase(PhaseType.ECCENTRIC, startSec, bottomSec)
            phases += Phase(PhaseType.CONCENTRIC, bottomSec, endSec)
            return RepSlice(index = 0, startSec = startSec, endSec = endSec, phases = phases)
        }

        var pauseStart = bottomIndex
        while (pauseStart > startIndex && abs(samples[pauseStart - 1].vyMps) <= config.vEps) pauseStart--
        var pauseEnd = bottomIndex
        while (pauseEnd < endIndex && abs(samples[pauseEnd + 1].vyMps) <= config.vEps) pauseEnd++
        val pauseStartSec = samples[pauseStart].tSec
        val pauseEndSec = samples[pauseEnd].tSec
        val usePause = pauseStartSec - startSec > 1e-9 &&
            pauseEndSec - pauseStartSec > 1e-9 &&
            endSec - pauseEndSec + 1e-9 >= config.minConcentricSec
        if (usePause) {
            phases += Phase(PhaseType.ECCENTRIC, startSec, pauseStartSec)
            phases += Phase(PhaseType.PAUSE, pauseStartSec, pauseEndSec)
            phases += Phase(PhaseType.CONCENTRIC, pauseEndSec, endSec)
        } else {
            phases += Phase(PhaseType.ECCENTRIC, startSec, bottomSec)
            phases += Phase(PhaseType.CONCENTRIC, bottomSec, endSec)
        }

        // Optional lockout: a near-zero-velocity run at the trailing top, capped like the
        // phase-run path so a long standing pause does not swallow the next rep.
        var lockoutStart = endIndex
        while (lockoutStart > bottomIndex && abs(samples[lockoutStart - 1].vyMps) <= config.vEps) lockoutStart--
        var lockoutEnd = endIndex
        while (lockoutEnd < samples.lastIndex && abs(samples[lockoutEnd + 1].vyMps) <= config.vEps) lockoutEnd++
        val lockoutStartSec = samples[lockoutStart].tSec
        val lockoutAvailableSec = samples[lockoutEnd].tSec - lockoutStartSec
        val trailingConcentric = phases.last()
        if (lockoutStart > bottomIndex &&
            lockoutAvailableSec + 1e-9 >= config.lockoutSec &&
            lockoutStartSec - trailingConcentric.startSec + 1e-9 >= config.minConcentricSec
        ) {
            val lockoutEndSec = minOf(samples[lockoutEnd].tSec, lockoutStartSec + config.lockoutSec)
            if (lockoutEndSec > lockoutStartSec) {
                phases[phases.lastIndex] = trailingConcentric.copy(endSec = lockoutStartSec)
                phases += Phase(PhaseType.LOCKOUT, lockoutStartSec, lockoutEndSec)
            }
        }
        return RepSlice(index = 0, startSec = startSec, endSec = endSec, phases = phases)
    }

    private fun smoothY(samples: List<MotionSample>): DoubleArray {
        val raw = DoubleArray(samples.size) { samples[it].yM }
        if (raw.size < 3) return raw
        val dt = medianSampleDt(samples)
        if (dt <= 0.0) return raw
        val radius = (EXTREMA_SMOOTHING_HALF_WINDOW_SEC / dt).roundToInt()
        if (radius <= 0) return raw
        return DoubleArray(raw.size) { index ->
            val from = (index - radius).coerceAtLeast(0)
            val to = (index + radius).coerceAtMost(raw.lastIndex)
            var sum = 0.0
            for (i in from..to) sum += raw[i]
            sum / (to - from + 1)
        }
    }

    private fun medianSampleDt(samples: List<MotionSample>): Double {
        if (samples.size < 2) return 0.0
        val deltas = DoubleArray(samples.size - 1) { samples[it + 1].tSec - samples[it].tSec }.sortedArray()
        return deltas[deltas.size / 2]
    }

    /**
     * Builds a clean alternating extremum list from smoothed y. The first/last sample is only
     * added as an extremum when that edge is at rest, so a clip that starts or ends mid-rep does
     * not fabricate a partial cycle.
     */
    private fun yExtrema(
        samples: List<MotionSample>,
        y: DoubleArray,
        minRomM: Double,
    ): List<YExtremum> {
        val n = y.size
        if (n < 3) return emptyList()
        val range = y.max() - y.min()
        // Movement smaller than this cannot contribute to a rep; treating it as flat also keeps
        // tracking noise from turning every wobble on a top/bottom plateau into an extremum.
        val flatEps = max(
            EXTREMA_FLAT_EPS_M,
            max(range * EXTREMA_FLAT_FRACTION, minRomM * EXTREMA_FLAT_ROM_FRACTION),
        )
        val turns = mutableListOf<YExtremum>()
        var direction = 0
        var lastTurnIndex = 0
        for (index in 1 until n) {
            val delta = y[index] - y[index - 1]
            if (abs(delta) <= flatEps) continue
            val sign = if (delta > 0) 1 else -1
            if (direction == 0) {
                direction = sign
                continue
            }
            if (sign == direction) continue
            val isBottom = direction < 0
            val extremumIndex = if (isBottom) {
                argMin(y, lastTurnIndex, index)
            } else {
                argMax(y, lastTurnIndex, index)
            }
            turns += YExtremum(extremumIndex, y[extremumIndex], isBottom)
            lastTurnIndex = extremumIndex
            direction = sign
        }
        if (turns.isEmpty()) return emptyList()

        val dt = medianSampleDt(samples)
        val result = mutableListOf<YExtremum>()
        if (edgeIsFlat(y, atStart = true, dt = dt, flatEps = flatEps)) {
            val first = turns.first()
            val index = if (first.isBottom) argMax(y, 0, first.index) else argMin(y, 0, first.index)
            if (index < first.index) {
                result += YExtremum(index, y[index], isBottom = !first.isBottom)
            }
        }
        result += turns
        if (edgeIsFlat(y, atStart = false, dt = dt, flatEps = flatEps)) {
            val last = turns.last()
            val index = if (direction > 0) argMax(y, last.index, n - 1) else argMin(y, last.index, n - 1)
            if (index > last.index) {
                result += YExtremum(index, y[index], isBottom = direction < 0)
            }
        }
        return result
    }

    private fun edgeIsFlat(y: DoubleArray, atStart: Boolean, dt: Double, flatEps: Double): Boolean {
        val windowSamples = if (dt > 0.0) {
            max(2, (EXTREMA_EDGE_FLAT_SEC / dt).roundToInt())
        } else {
            2
        }
        val count = minOf(y.size, windowSamples)
        val from = if (atStart) 0 else y.size - count
        val to = if (atStart) count - 1 else y.lastIndex
        var minimum = y[from]
        var maximum = y[from]
        for (index in from..to) {
            minimum = minOf(minimum, y[index])
            maximum = maxOf(maximum, y[index])
        }
        return maximum - minimum <= max(flatEps, EXTREMA_EDGE_FLAT_M)
    }

    /**
     * Removes weak alternating extrema one at a time, always dropping the smallest excursion
     * first. This clears noise wiggles before a real top/bottom (whose neighbours are the full
     * opposite excursion) is judged. What remains is a clean zigzag whose every step is at least
     * [minRomM], i.e. the amplitude requirement.
     */
    private fun filterYExtrema(extrema: List<YExtremum>, minRomM: Double): List<YExtremum> {
        val current = extrema.toMutableList()
        while (current.size >= 3) {
            var weakestIndex = 0
            var weakestAmplitude = Double.POSITIVE_INFINITY
            for (index in current.indices) {
                val amplitude = extremumAmplitude(current, index)
                if (amplitude < weakestAmplitude) {
                    weakestAmplitude = amplitude
                    weakestIndex = index
                }
            }
            if (weakestAmplitude + 1e-9 >= minRomM) break
            current.removeAt(weakestIndex)
        }
        val alternating = mutableListOf<YExtremum>()
        for (extremum in current) {
            val previous = alternating.lastOrNull()
            if (previous != null && previous.isBottom == extremum.isBottom) {
                if (isMoreExtreme(extremum, previous)) alternating[alternating.lastIndex] = extremum
            } else {
                alternating += extremum
            }
        }
        return alternating
    }

    /** Smallest y excursion to either neighbour; a boundary extremum only has one side. */
    private fun extremumAmplitude(extrema: List<YExtremum>, index: Int): Double {
        val extremum = extrema[index]
        val previous = extrema.getOrNull(index - 1)
        val next = extrema.getOrNull(index + 1)
        val previousAmplitude = previous?.let { abs(extremum.y - it.y) }
        val nextAmplitude = next?.let { abs(extremum.y - it.y) }
        return when {
            previousAmplitude != null && nextAmplitude != null -> minOf(previousAmplitude, nextAmplitude)
            previousAmplitude != null -> previousAmplitude
            nextAmplitude != null -> nextAmplitude
            else -> 0.0
        }
    }

    private fun isMoreExtreme(candidate: YExtremum, current: YExtremum): Boolean =
        if (candidate.isBottom) candidate.y < current.y else candidate.y > current.y

    private fun argMin(values: DoubleArray, from: Int, to: Int): Int {
        var best = from.coerceIn(0, values.lastIndex)
        for (index in (best + 1)..to.coerceAtMost(values.lastIndex)) {
            if (values[index] < values[best]) best = index
        }
        return best
    }

    private fun argMax(values: DoubleArray, from: Int, to: Int): Int {
        var best = from.coerceIn(0, values.lastIndex)
        for (index in (best + 1)..to.coerceAtMost(values.lastIndex)) {
            if (values[index] > values[best]) best = index
        }
        return best
    }

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

    /**
     * Deadlift reps are positive-velocity runs. The bar starts on the floor, so a pull has no
     * eccentric phase to pair; the descent between reps is the separator. Entering a run needs
     * `vy > vEps`, leaving it only needs `vy <= vEps / 2` so a small dip mid-pull cannot split a
     * rep. Very short dips between two positive runs are also merged when the bar has not dropped
     * meaningfully.
     */
    private fun segmentDeadliftsByVelocity(
        samples: List<MotionSample>,
        config: PhaseConfig,
    ): MutableSegmentation {
        val runs = mergeDeadliftVelocityRuns(
            positiveVelocityRuns(samples, config.vEps),
            samples,
            config,
        )
        val reps = mutableListOf<RepSlice>()
        var incomplete = 0
        var noLockout = 0
        runs.forEachIndexed { runIndex, run ->
            // Never let the bottom lookback reach into the previous pull's samples.
            val lookbackFloor = runs.getOrNull(runIndex - 1)?.endIndex?.plus(1) ?: 0
            // The concentric phase of a lift runs from the lowest point to the highest point; the
            // velocity run only says where the pull is, not where it ends.
            val topLimit = runs.getOrNull(runIndex + 1)?.startIndex?.minus(1) ?: samples.lastIndex
            val candidate = deadliftRunToRep(run, samples, config, lookbackFloor, topLimit)
            if (candidate == null) {
                incomplete++
            } else {
                reps += candidate.copy(index = reps.size + 1)
                if (candidate.phases.none { it.type == PhaseType.LOCKOUT }) noLockout++
            }
        }
        return MutableSegmentation(reps, incomplete, noLockout)
    }

    private fun positiveVelocityRuns(samples: List<MotionSample>, vEps: Double): List<VelocityRun> {
        val exitEps = vEps * DEADLIFT_EXIT_EPS_FRACTION
        val runs = mutableListOf<VelocityRun>()
        var index = 0
        while (index < samples.size) {
            if (samples[index].vyMps <= vEps) {
                index++
                continue
            }
            var end = index
            var next = index + 1
            while (next < samples.size && samples[next].vyMps > exitEps) {
                end = next
                next++
            }
            runs += VelocityRun(index, end)
            index = next
        }
        return runs
    }

    /**
     * Joins two positive runs separated by a short non-positive gap when the bar barely dropped
     * across it. A real floor-to-top cycle loses about a full ROM, far above the fraction gate.
     */
    private fun mergeDeadliftVelocityRuns(
        runs: List<VelocityRun>,
        samples: List<MotionSample>,
        config: PhaseConfig,
    ): List<VelocityRun> {
        if (runs.size < 2) return runs
        val merged = mutableListOf(runs.first())
        for (run in runs.drop(1)) {
            val previous = merged.last()
            val gapSec = samples[run.startIndex].tSec - samples[previous.endIndex].tSec
            val gapSamples = samples.subList(previous.endIndex, run.startIndex + 1)
            // Only the drop below the previous rep's top matters; noise inside the gap does not.
            val yDrop = samples[previous.endIndex].yM - gapSamples.minOf { it.yM }
            if (gapSec + 1e-9 <= DEADLIFT_GAP_MERGE_SEC &&
                yDrop + 1e-9 < config.minRomM * DEADLIFT_GAP_MERGE_ROM_FRACTION
            ) {
                merged[merged.lastIndex] = VelocityRun(previous.startIndex, run.endIndex)
            } else {
                merged += run
            }
        }
        return merged
    }

    private fun deadliftRunToRep(
        run: VelocityRun,
        samples: List<MotionSample>,
        config: PhaseConfig,
        lookbackFloorIndex: Int,
        topLimitIndex: Int,
    ): RepSlice? {
        // The bottom is the local y minimum in the half second before the pull starts.
        var bottomIndex = run.startIndex
        var index = run.startIndex - 1
        while (index >= lookbackFloorIndex &&
            samples[run.startIndex].tSec - samples[index].tSec <= DEADLIFT_BOTTOM_LOOKBACK_SEC + 1e-9
        ) {
            // Ties go to the earliest sample so a floor plateau is fully included.
            if (samples[index].yM <= samples[bottomIndex].yM) bottomIndex = index
            index--
        }
        val startSec = samples[bottomIndex].tSec
        // Top of the pull: the highest sample before the next pull starts, never behind the run.
        var repEndIndex = (run.endIndex + 1).coerceAtMost(samples.lastIndex)
        val topCap = topLimitIndex.coerceIn(repEndIndex, samples.lastIndex)
        for (index in repEndIndex..topCap) {
            if (samples[index].yM > samples[repEndIndex].yM) repEndIndex = index
        }
        val endSec = samples[repEndIndex].tSec
        val repSamples = samples.subList(bottomIndex, repEndIndex + 1)
        val romM = repSamples.maxOf { it.yM } - repSamples.minOf { it.yM }
        if (endSec - startSec + 1e-9 < config.minRepSec) return null

        // The pull itself is the part above the velocity threshold, not the bottom lookback.
        val concentricSec = endSec - samples[run.startIndex].tSec
        if (concentricSec + 1e-9 < config.minConcentricSec) return null

        if (romM + 1e-9 < config.minRomM) return null
        val phases = mutableListOf(Phase(PhaseType.CONCENTRIC, startSec, endSec))
        deadliftLockout(repEndIndex, samples, config)?.let { phases += it }
        return RepSlice(index = 0, startSec = startSec, endSec = endSec, phases = phases)
    }

    /**
     * Optional lockout: the first near-zero-velocity pause after the pull, capped to
     * [PhaseConfig.lockoutSec]. Deadlift reps stay valid without it, so callers only get a hint.
     */
    private fun deadliftLockout(
        fromIndex: Int,
        samples: List<MotionSample>,
        config: PhaseConfig,
    ): Phase? {
        var pauseStart = fromIndex
        while (pauseStart < samples.size && kotlin.math.abs(samples[pauseStart].vyMps) > config.vEps) {
            pauseStart++
        }
        if (pauseStart >= samples.size) return null
        var pauseEnd = pauseStart
        var index = pauseStart + 1
        while (index < samples.size && kotlin.math.abs(samples[index].vyMps) <= config.vEps) {
            pauseEnd = index
            index++
        }
        val startSec = samples[pauseStart].tSec
        val availableSec = samples[pauseEnd].tSec - startSec
        if (availableSec + 1e-9 < config.lockoutSec) return null
        val endSec = minOf(samples[pauseEnd].tSec, startSec + config.lockoutSec)
        return if (endSec > startSec) Phase(PhaseType.LOCKOUT, startSec, endSec) else null
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
        requireTurnaround: Boolean = true,
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

        if (requireTurnaround && !hasTurnaround(repSamples, ordered, config)) return null

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

    /**
     * Drops candidates whose vertical travel is implausibly small relative to the rest of the
     * set. Requires at least [ROM_OUTLIER_MIN_REPS] candidates; the absolute [PhaseConfig.minRomM]
     * floor wins when it is stricter than half the median.
     */
    private fun dropRomOutliers(
        reps: List<RepSlice>,
        samples: List<MotionSample>,
        config: PhaseConfig,
    ): Pair<List<RepSlice>, Int> {
        if (reps.size < ROM_OUTLIER_MIN_REPS) {
            return reps.mapIndexed { index, rep -> rep.copy(index = index + 1) } to 0
        }
        val roms = reps.map { repRomM(it, samples) }
        val sorted = roms.sorted()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }
        val threshold = max(config.minRomM, median * ROM_OUTLIER_MEDIAN_FRACTION)
        var dropped = 0
        val kept = buildList {
            reps.forEachIndexed { index, rep ->
                if (roms[index] + 1e-9 >= threshold) add(rep) else dropped++
            }
        }
        return kept.mapIndexed { index, rep -> rep.copy(index = index + 1) } to dropped
    }

    private fun repRomM(rep: RepSlice, samples: List<MotionSample>): Double {
        val repSamples = samples.filter { it.tSec >= rep.startSec && it.tSec <= rep.endSec }
        if (repSamples.size < 2) return 0.0
        return repSamples.maxOf { it.yM } - repSamples.minOf { it.yM }
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

    /** Below this candidate count the set median is not meaningful. */
    private const val ROM_OUTLIER_MIN_REPS = 3

    /** A rep under half the set's median ROM is treated as a partial pull. */
    private const val ROM_OUTLIER_MEDIAN_FRACTION = 0.5

    /** Hysteresis: a running pull only ends once velocity falls below this fraction of vEps. */
    private const val DEADLIFT_EXIT_EPS_FRACTION = 0.5

    /**
     * Safety net only: a pull that dips at the sticking point can stall for several tenths of a
     * second. What actually separates one pull from two is the bar drop across the gap, below.
     */
    private const val DEADLIFT_GAP_MERGE_SEC = 1.0

    /** ...but only when the bar dropped less than this fraction of minRomM across the gap. */
    private const val DEADLIFT_GAP_MERGE_ROM_FRACTION = 0.25

    /** How far before a pull to look for the floor (bottom) sample. */
    private const val DEADLIFT_BOTTOM_LOOKBACK_SEC = 0.5

    /** Half-window of the extra moving average applied before y-extrema detection. */
    private const val EXTREMA_SMOOTHING_HALF_WINDOW_SEC = 0.12

    /** y deltas under this are treated as flat terrain while finding turnarounds. */
    private const val EXTREMA_FLAT_EPS_M = 1e-4

    /** Flat threshold also scales with the track's total travel for very noisy tracks. */
    private const val EXTREMA_FLAT_FRACTION = 1e-4

    /** ...and with the required ROM: wiggles below 5% of minRomM are never meaningful. */
    private const val EXTREMA_FLAT_ROM_FRACTION = 0.05

    /** How much of the start/end must be at rest before a boundary extremum is trusted. */
    private const val EXTREMA_EDGE_FLAT_SEC = 0.20

    /** Maximum y variation inside that rest window. */
    private const val EXTREMA_EDGE_FLAT_M = 0.02

    /** Same-kind extrema closer than this (or minRepSec) cannot be separate reps. */
    private const val EXTREMA_MIN_SEPARATION_SEC = 0.35
}
