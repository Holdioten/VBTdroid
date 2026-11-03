package com.vbtdroid.core.phases

import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.model.Lift

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
    val vEps: Double = 0.03,
    val minPhaseSec: Double = 0.12,
    val lockoutSec: Double = 0.10,
)

data class PhaseSegmentation(
    val reps: List<RepSlice>,
    val warnings: List<String>,
)

object PhaseSegmenter {
    const val INCOMPLETE_REP_NO_LOCKOUT = "INCOMPLETE_REP_NO_LOCKOUT"

    fun segment(
        samples: List<MotionSample>,
        lift: Lift,
        config: PhaseConfig = PhaseConfig(),
    ): List<RepSlice> = segmentDetailed(samples, lift, config).reps

    /** Formal reps always end in lockout; trailing or interrupted movement is reported, not accepted. */
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

        // Lockout has a 0.10 s threshold and must be protected before generic 0.12 s run merging.
        val runsWithLockouts = detectLockouts(classify(samples, config.vEps), config.lockoutSec)
        val runs = mergeShortRuns(runsWithLockouts, config.minPhaseSec)
        val result = when (lift) {
            Lift.SQUAT, Lift.BENCH -> segmentEccentricLifts(runs, config.lockoutSec)
            Lift.DEADLIFT -> segmentDeadlifts(runs, config.lockoutSec)
        }
        return PhaseSegmentation(
            reps = result.reps,
            warnings = if (result.incompleteCount > 0) listOf(INCOMPLETE_REP_NO_LOCKOUT) else emptyList(),
        )
    }

    /** Duration-margin confidence for phase review; manual boundaries always require review. */
    fun confidence(rep: RepSlice, config: PhaseConfig = PhaseConfig()): Double {
        if (rep.phases.any { it.manual }) return 0.7
        val margin = rep.phases.minOfOrNull { phase ->
            val required = if (phase.type == PhaseType.LOCKOUT) config.lockoutSec else config.minPhaseSec
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

    private fun segmentEccentricLifts(runs: List<Run>, lockoutSec: Double): MutableSegmentation {
        val reps = mutableListOf<RepSlice>()
        var phases = mutableListOf<Phase>()
        var incomplete = 0

        fun finish() {
            if (phases.isNotEmpty()) {
                if (phases.any { it.type == PhaseType.ECCENTRIC } &&
                    phases.any { it.type == PhaseType.CONCENTRIC } &&
                    phases.any { it.type == PhaseType.LOCKOUT }
                ) {
                    reps += RepSlice(reps.size + 1, phases.first().startSec, phases.last().endSec, phases.toList())
                } else {
                    incomplete++
                }
                phases = mutableListOf()
            }
        }

        for (run in runs) {
            if (run.type == PhaseType.ECCENTRIC) {
                finish()
                phases += run.toPhase()
                continue
            }
            if (phases.isEmpty()) continue
            if (run.type == PhaseType.LOCKOUT && phases.any { it.type == PhaseType.CONCENTRIC }) {
                phases += run.toPhase().let { it.copy(endSec = minOf(it.endSec, it.startSec + lockoutSec)) }
                finish()
            } else {
                phases += run.toPhase()
            }
        }
        finish()
        return MutableSegmentation(reps, incomplete)
    }

    private fun segmentDeadlifts(runs: List<Run>, lockoutSec: Double): MutableSegmentation {
        val reps = mutableListOf<RepSlice>()
        var phases = mutableListOf<Phase>()
        var incomplete = 0

        fun finish() {
            if (phases.isNotEmpty()) {
                if (phases.any { it.type == PhaseType.CONCENTRIC } && phases.any { it.type == PhaseType.LOCKOUT }) {
                    reps += RepSlice(reps.size + 1, phases.first().startSec, phases.last().endSec, phases.toList())
                } else {
                    incomplete++
                }
                phases = mutableListOf()
            }
        }

        for (run in runs) {
            if (run.type == PhaseType.CONCENTRIC) {
                finish()
                phases += run.toPhase()
                continue
            }
            if (phases.isEmpty()) continue
            if (run.type == PhaseType.LOCKOUT && phases.any { it.type == PhaseType.CONCENTRIC }) {
                phases += run.toPhase().let { it.copy(endSec = minOf(it.endSec, it.startSec + lockoutSec)) }
                finish()
            } else if (run.type != PhaseType.ECCENTRIC) {
                phases += run.toPhase()
            }
        }
        finish()
        return MutableSegmentation(reps, incomplete)
    }

    private fun Run.toPhase() = Phase(type, startSec, endSec)
}
