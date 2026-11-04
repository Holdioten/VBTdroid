package com.vbtdroid.app.ui.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlateDiameterTest {
    @Test
    fun `centimetres are converted to metres for the analysis request`() {
        assertEquals(0.45, plateDiameterMeters(45.0)!!, 1e-9)
        assertEquals(0.5, plateDiameterMeters(50.0)!!, 1e-9)
        assertEquals(0.001, plateDiameterMeters(0.1)!!, 1e-12)
    }

    @Test
    fun `invalid centimetre input is rejected`() {
        assertNull(plateDiameterMeters(null))
        assertNull(plateDiameterMeters(0.0))
        assertNull(plateDiameterMeters(-45.0))
        assertNull(plateDiameterMeters(Double.NaN))
        assertNull(plateDiameterMeters(Double.POSITIVE_INFINITY))
    }
}
