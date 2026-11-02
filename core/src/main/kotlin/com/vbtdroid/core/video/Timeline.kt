package com.vbtdroid.core.video

object Timeline {
    fun normalizePts(timestampsUs: LongArray): DoubleArray {
        for (index in 1 until timestampsUs.size) {
            require(timestampsUs[index] > timestampsUs[index - 1]) {
                "Presentation timestamps must be strictly increasing"
            }
        }
        return DoubleArray(timestampsUs.size) { index -> timestampsUs[index] / 1_000_000.0 }
    }
}
