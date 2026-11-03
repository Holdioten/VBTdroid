package com.vbtdroid.core.validation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AgreementTest {
    @Test
    fun `perfect agreement`() {
        val result = Agreement.blandAltman(
            doubleArrayOf(0.5, 0.6, 0.7),
            doubleArrayOf(0.5, 0.6, 0.7),
        )

        assertEquals(0.0, result.bias, 1e-9)
        assertEquals(0.0, result.sdDiff, 1e-9)
        assertEquals(0.0, result.lowerLoa, 1e-9)
        assertEquals(0.0, result.upperLoa, 1e-9)
        assertEquals(1.0, result.icc, 1e-9)
    }

    @Test
    fun `constant bias and sample deviation`() {
        val result = Agreement.blandAltman(
            doubleArrayOf(0.5, 0.6, 0.7),
            doubleArrayOf(0.52, 0.62, 0.72),
        )

        assertEquals(0.02, result.bias, 1e-9)
        assertEquals(0.0, result.sdDiff, 1e-9)
        assertEquals(0.02, result.lowerLoa, 1e-9)
        assertEquals(0.02, result.upperLoa, 1e-9)
    }

    @Test
    fun `requires non-empty equal length inputs`() {
        assertThrows(IllegalArgumentException::class.java) {
            Agreement.blandAltman(doubleArrayOf(), doubleArrayOf())
        }
        assertThrows(IllegalArgumentException::class.java) {
            Agreement.blandAltman(doubleArrayOf(1.0), doubleArrayOf(1.0, 2.0))
        }
    }
}
