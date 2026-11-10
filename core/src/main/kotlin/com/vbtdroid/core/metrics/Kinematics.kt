package com.vbtdroid.core.metrics

import com.vbtdroid.core.model.BarSample
import com.vbtdroid.core.signal.LocalPolynomial
import com.vbtdroid.core.signal.SampleWindow

data class MotionSample(
    val tSec: Double,
    val xM: Double,
    val yM: Double,
    val vxMps: Double,
    val vyMps: Double,
    val axMps2: Double?,
    val ayMps2: Double?,
)

object Kinematics {
    /**
     * Derivative estimation whose smoothing window is given in seconds.
     *
     * Use this overload in the pipeline: a fixed sample count would change the effective
     * smoothing time with the frame rate and make 30 fps and 60 fps clips disagree.
     */
    fun deriveByTime(
        samples: List<BarSample>,
        windowSec: Double,
        polyOrder: Int = 2,
    ): List<MotionSample> {
        require(windowSec.isFinite() && windowSec > 0.0) { "windowSec must be positive and finite" }
        if (samples.isEmpty()) return emptyList()
        val times = DoubleArray(samples.size) { samples[it].tSec }
        return derive(samples, SampleWindow.samplesFor(times, windowSec), polyOrder)
    }

    fun derive(samples: List<BarSample>, window: Int = 7, polyOrder: Int = 2): List<MotionSample> {
        require(window > 0) { "window must be positive" }
        require(polyOrder >= 0) { "polyOrder must be non-negative" }
        if (samples.isEmpty()) return emptyList()

        for (index in samples.indices) {
            require(samples[index].tSec.isFinite()) { "timestamps must be finite" }
            if (index > 0) {
                require(samples[index].tSec > samples[index - 1].tSec) {
                    "timestamps must be strictly increasing"
                }
            }
        }

        val times = DoubleArray(samples.size) { samples[it].tSec }
        val x = DoubleArray(samples.size) { samples[it].xM }
        val y = DoubleArray(samples.size) { samples[it].yM }
        return samples.indices.map { index ->
            val xFit = LocalPolynomial.fit(times, x, index, window, polyOrder)
            val yFit = LocalPolynomial.fit(times, y, index, window, polyOrder)
            MotionSample(
                tSec = samples[index].tSec,
                xM = xFit.value,
                yM = yFit.value,
                vxMps = xFit.firstDerivative,
                vyMps = yFit.firstDerivative,
                axMps2 = xFit.secondDerivative,
                ayMps2 = yFit.secondDerivative,
            )
        }
    }
}
