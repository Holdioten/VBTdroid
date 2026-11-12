package com.vbtdroid.pcreplay

import com.vbtdroid.core.calibration.Calibration
import com.vbtdroid.core.metrics.Kinematics
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.model.PlateObservation
import com.vbtdroid.core.phases.PhaseSegmenter
import com.vbtdroid.core.signal.TrackSmoother
import java.io.File

/**
 * PC replay of the App's analysis maths.
 *
 * The plate observations come from the App itself (`cache/debug-observations.csv`, written by
 * AnalysisForegroundService.writeDebugDump); everything after that - track smoothing, calibration,
 * kinematics and phase segmentation - runs here through the very same `core` code the App calls,
 * so a change to `core` can be evaluated on the PC without an emulator.
 *
 * Usage:
 *   ./gradlew :tools:pcreplay:run --args="--obs obs.csv --plate-m 0.45 --angle 45 --lift DEADLIFT"
 */
private const val MEAN_WINDOW_SEC = 0.30
private const val PEAK_WINDOW_SEC = 0.15
private const val TRACK_SMOOTH_WINDOW_SEC = 0.18
private const val INITIAL_DIAMETER_SEC = 0.30
private const val MIN_INITIAL_DIAMETER_SAMPLES = 3
private const val FILTER_ORDER = 2

private fun factor(lift: Lift): Double = if (lift == Lift.BENCH) 1.15 else 1.0

private fun median(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

private fun readObservations(path: String): List<PlateObservation> {
    val lines = File(path).readLines().drop(1).filter { it.isNotBlank() }
    return lines.map { line ->
        val parts = line.split(',')
        PlateObservation(
            tSec = parts[0].trim().toDouble(),
            xPx = parts[1].trim().toDouble(),
            yPx = parts[2].trim().toDouble(),
            majorAxisPx = parts[3].trim().toDouble(),
            confidence = parts[4].trim().toDouble(),
        )
    }
}

fun main(args: Array<String>) {
    var obsPath = ""
    var plateDiameterM = 0.45
    var cameraAngleDeg = 45.0
    var lift = Lift.DEADLIFT
    var outDir = "/tmp/pcreplay"

    var index = 0
    while (index < args.size) {
        when (args[index]) {
            "--obs" -> obsPath = args[++index]
            "--plate-m" -> plateDiameterM = args[++index].toDouble()
            "--angle" -> cameraAngleDeg = args[++index].toDouble()
            "--lift" -> lift = Lift.valueOf(args[++index].uppercase())
            "--out" -> outDir = args[++index]
            else -> error("unknown argument ${args[index]}")
        }
        index++
    }
    check(obsPath.isNotBlank()) { "--obs is required" }

    val observations = readObservations(obsPath)
    check(observations.size >= 2) { "at least two observations required (got ${observations.size})" }

    // Same order as AnalysisPipeline: smooth the pixel track, calibrate from the box height of the
    // first frames, then derive motion in metres per second.
    val cleaned = TrackSmoother.smoothByTime(
        observations,
        windowSec = TRACK_SMOOTH_WINDOW_SEC * factor(lift),
        polyOrder = FILTER_ORDER,
    )
    val firstFrame = observations.first().tSec
    val initialDiameters = observations
        .filter { it.tSec <= firstFrame + INITIAL_DIAMETER_SEC + 1e-9 }
        .map { it.majorAxisPx }
    val diameterPx = if (initialDiameters.size >= MIN_INITIAL_DIAMETER_SAMPLES) {
        median(initialDiameters)
    } else {
        cleaned.first().majorAxisPx
    }
    val calibration = Calibration.fromPlate(
        plateDiameterM = plateDiameterM,
        majorAxisPx = diameterPx,
        cameraAngleDeg = cameraAngleDeg,
    )
    val barSamples = cleaned.map(calibration::toMeters)
    val motionSamples = Kinematics.deriveByTime(
        barSamples,
        windowSec = MEAN_WINDOW_SEC * factor(lift),
        polyOrder = FILTER_ORDER,
    )
    val segmentation = PhaseSegmenter.segmentDetailed(motionSamples, lift)

    File(outDir).mkdirs()
    File(outDir, "motion.csv").writeText(
        buildString {
            appendLine("t,y_m,vy_mps")
            motionSamples.forEach { appendLine("${it.tSec},${it.yM},${it.vyMps}") }
        },
    )
    File(outDir, "reps.csv").writeText(
        buildString {
            appendLine("index,start_sec,end_sec,rom_cm,mcv_mps")
            segmentation.reps.forEach { rep ->
                val window = motionSamples.filter { it.tSec >= rep.startSec && it.tSec <= rep.endSec }
                val rom = if (window.size >= 2) window.maxOf { it.yM } - window.minOf { it.yM } else 0.0
                val duration = if (window.size >= 2) window.last().tSec - window.first().tSec else 0.0
                val mcv = if (duration > 0) rom / duration else 0.0
                appendLine("${rep.index},${rep.startSec},${rep.endSec},${rom * 100},$mcv")
            }
        },
    )

    println("observations=${observations.size} motion=${motionSamples.size} reps=${segmentation.reps.size}")
    segmentation.reps.forEach { rep ->
        val window = motionSamples.filter { it.tSec >= rep.startSec && it.tSec <= rep.endSec }
        val rom = if (window.size >= 2) window.maxOf { it.yM } - window.minOf { it.yM } else 0.0
        val duration = if (window.size >= 2) window.last().tSec - window.first().tSec else 0.0
        println(
            "  #${rep.index} ${"%.2f".format(rep.startSec)}-${"%.2f".format(rep.endSec)} " +
                "rom=${"%.1f".format(rom * 100)}cm mcv=${"%.3f".format(if (duration > 0) rom / duration else 0.0)}",
        )
    }
    if (segmentation.warnings.isNotEmpty()) println("warnings=${segmentation.warnings}")
}
