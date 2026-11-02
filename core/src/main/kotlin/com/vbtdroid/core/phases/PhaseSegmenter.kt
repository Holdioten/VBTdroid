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

object PhaseSegmenter {
    fun segment(
        samples: List<MotionSample>,
        lift: Lift,
        config: PhaseConfig = PhaseConfig(),
    ): List<RepSlice> {
        require(config.vEps.isFinite() && config.vEps >= 0.0) { "vEps must be finite and non-negative" }
        require(config.minPhaseSec.isFinite() && config.minPhaseSec >= 0.0) {
            "minPhaseSec must be finite and non-negative"
        }
        require(config.lockoutSec.isFinite() && config.lockoutSec >= 0.0) {
            "lockoutSec must be finite and non-negative"
        }
        if (samples.isEmpty()) return emptyList()
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

        val runs = mergeShortRuns(classify(samples, config.vEps), config.minPhaseSec)
        return when (lift) {
            Lift.SQUAT, Lift.BENCH -> segmentEccentricLifts(runs, config.lockoutSec)
            Lift.DEADLIFT -> segmentDeadlifts(runs, config.lockoutSec)
        }
    }

    private data class Run(
        val type: PhaseType,
        val startSec: Double,
        val endSec: Double,
    ) {
        val durationSec: Double get() = endSec - startSec
    }

    private fun classify(samples: List<MotionSample>, vEps: Double): List<Run> {
        val runs = mutableListOf<Run>()
        val exitEps = vEps * 0.5
        var type = phaseFor(samples.first().vyMps, vEps)
        var start = samples.first().tSec

        for (index in 1 until samples.size) {
            val vyMps = samples[index].vyMps
            val nextType = when (type) {
                PhaseType.ECCENTRIC -> if (vyMps >= -exitEps) {
                    phaseFor(vyMps, vEps)
                } else {
                    PhaseType.ECCENTRIC
                }
                PhaseType.CONCENTRIC -> if (vyMps <= exitEps) {
                    phaseFor(vyMps, vEps)
                } else {
                    PhaseType.CONCENTRIC
                }
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

    /** 用最短持续时间抑制阈值抖动，并丢弃孤立速度尖峰。 */
    private fun mergeShortRuns(source: List<Run>, minPhaseSec: Double): List<Run> {
        val runs = source.toMutableList()
        if (runs.size == 1 && runs.single().durationSec < minPhaseSec) return emptyList()
        var index = 0
        while (index < runs.size && runs.size > 1) {
            if (runs[index].durationSec >= minPhaseSec) {
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
        return if (runs.size == 1 && runs.single().durationSec < minPhaseSec) emptyList() else runs
    }

    private fun segmentEccentricLifts(runs: List<Run>, lockoutSec: Double): List<RepSlice> {
        val reps = mutableListOf<RepSlice>()
        var phases = mutableListOf<Phase>()

        fun finish() {
            if (phases.isNotEmpty()) {
                reps += RepSlice(
                    index = reps.size + 1,
                    startSec = phases.first().startSec,
                    endSec = phases.last().endSec,
                    phases = phases.toList(),
                )
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

            if (run.type == PhaseType.PAUSE && phases.any { it.type == PhaseType.CONCENTRIC } &&
                run.durationSec >= lockoutSec
            ) {
                val lockoutEnd = minOf(run.endSec, run.startSec + lockoutSec)
                if (lockoutEnd > run.startSec) phases += Phase(PhaseType.LOCKOUT, run.startSec, lockoutEnd)
                finish()
            } else {
                phases += run.toPhase()
            }
        }
        finish()
        return reps
    }

    private fun segmentDeadlifts(runs: List<Run>, lockoutSec: Double): List<RepSlice> {
        val reps = mutableListOf<RepSlice>()
        var phases = mutableListOf<Phase>()

        fun finish() {
            if (phases.isNotEmpty()) {
                reps += RepSlice(
                    index = reps.size + 1,
                    startSec = phases.first().startSec,
                    endSec = phases.last().endSec,
                    phases = phases.toList(),
                )
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

            if (run.type == PhaseType.PAUSE && phases.any { it.type == PhaseType.CONCENTRIC } &&
                run.durationSec >= lockoutSec
            ) {
                val lockoutEnd = minOf(run.endSec, run.startSec + lockoutSec)
                if (lockoutEnd > run.startSec) phases += Phase(PhaseType.LOCKOUT, run.startSec, lockoutEnd)
                finish()
            } else if (run.type != PhaseType.ECCENTRIC) {
                phases += run.toPhase()
            }
        }
        finish()
        return reps
    }

    private fun Run.toPhase() = Phase(type, startSec, endSec)
}
