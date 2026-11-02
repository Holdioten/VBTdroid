package com.vbtdroid.core.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LvpTest {
    @Test
    fun `linear fit and 1rm`() {
        val model = Lvp.fit(
            listOf(LvpPoint(100.0, 0.8), LvpPoint(120.0, 0.65), LvpPoint(140.0, 0.5)),
            v1rmMps = 0.3
        )

        assertEquals(-0.0075, model.slope, 1e-9)
        assertEquals(1.55, model.intercept, 1e-9)
        assertEquals(1.0, model.r2, 1e-9)
        assertEquals(0.3, model.v1rmMps, 1e-9)
        assertEquals(166.666666, model.estimated1RmKg, 1e-4)
    }

    @Test
    fun `requires at least three points`() {
        assertThrows(IllegalArgumentException::class.java) {
            Lvp.fit(listOf(LvpPoint(100.0, 0.8), LvpPoint(120.0, 0.7)), 0.3)
        }
    }

    @Test
    fun `requires at least two distinct loads`() {
        assertThrows(IllegalArgumentException::class.java) {
            Lvp.fit(
                listOf(LvpPoint(100.0, 0.8), LvpPoint(100.0, 0.7), LvpPoint(100.0, 0.6)),
                0.3
            )
        }
    }

    @Test
    fun `requires positive v1rm velocity`() {
        assertThrows(IllegalArgumentException::class.java) {
            Lvp.fit(
                listOf(LvpPoint(100.0, 0.8), LvpPoint(120.0, 0.7), LvpPoint(140.0, 0.6)),
                0.0
            )
        }
    }

    @Test
    fun `requires negative slope`() {
        assertThrows(IllegalArgumentException::class.java) {
            Lvp.fit(
                listOf(LvpPoint(100.0, 0.5), LvpPoint(120.0, 0.6), LvpPoint(140.0, 0.7)),
                0.3
            )
        }
    }
}
