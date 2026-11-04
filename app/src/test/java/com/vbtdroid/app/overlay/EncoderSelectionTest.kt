package com.vbtdroid.app.overlay

import android.media.MediaCodecInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncoderSelectionTest {
    private val semiPlanar = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
    private val planar = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar

    @Test
    fun `prefers hardware encoder listed after software encoder`() {
        val selection = selectPreferredEncoder(
            listOf(
                candidate("c2.android.avc.encoder", semiPlanar, hardware = false, softwareOnly = true),
                candidate("c2.qti.avc.encoder", semiPlanar, hardware = true, softwareOnly = false),
            ),
        )

        assertEquals("c2.qti.avc.encoder", selection?.name)
        assertEquals(semiPlanar, selection?.colorFormat)
    }

    @Test
    fun `hardware planar beats software semi-planar`() {
        val selection = selectPreferredEncoder(
            listOf(
                candidate("OMX.google.h264.encoder", semiPlanar, hardware = false, softwareOnly = true),
                candidate("OMX.qcom.video.encoder.avc", planar, hardware = true, softwareOnly = false),
            ),
        )

        assertEquals("OMX.qcom.video.encoder.avc", selection?.name)
        assertEquals(planar, selection?.colorFormat)
    }

    @Test
    fun `semi-planar preferred among equally ranked encoders`() {
        val selection = selectPreferredEncoder(
            listOf(
                candidate("c2.qti.avc.encoder", planar, hardware = true, softwareOnly = false),
                candidate("OMX.qcom.video.encoder.avc", semiPlanar, hardware = true, softwareOnly = false),
            ),
        )

        assertEquals("OMX.qcom.video.encoder.avc", selection?.name)
        assertEquals(semiPlanar, selection?.colorFormat)
    }

    @Test
    fun `unknown flags fall back to vendor naming heuristic`() {
        val selection = selectPreferredEncoder(
            listOf(
                candidate("OMX.google.h264.encoder", semiPlanar),
                candidate("c2.android.avc.encoder", semiPlanar),
                candidate("OMX.qcom.video.encoder.avc", semiPlanar),
            ),
        )

        assertEquals("OMX.qcom.video.encoder.avc", selection?.name)
    }

    @Test
    fun `software encoder still used when nothing else exists`() {
        val selection = selectPreferredEncoder(
            listOf(
                candidate("c2.android.avc.encoder", planar, hardware = false, softwareOnly = true),
            ),
        )

        assertEquals("c2.android.avc.encoder", selection?.name)
        assertEquals(planar, selection?.colorFormat)
    }

    @Test
    fun `ignores decoders non-avc encoders and encoders without yuv420`() {
        assertNull(
            selectPreferredEncoder(
                listOf(
                    candidate("c2.qti.avc.decoder", semiPlanar, hardware = true, softwareOnly = false)
                        .copy(isEncoder = false),
                    candidate("c2.qti.hevc.encoder", semiPlanar, hardware = true, softwareOnly = false)
                        .copy(supportsAvc = false),
                    candidate("c2.qti.avc.encoder", MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                        .copy(isHardwareAccelerated = true, isSoftwareOnly = false),
                ),
            ),
        )
    }

    @Test
    fun `unknown flags without vendor name are not treated as software only`() {
        val selection = selectPreferredEncoder(
            listOf(
                candidate("OMX.google.h264.encoder", semiPlanar),
                candidate("acme.avc.encoder", semiPlanar),
            ),
        )

        assertTrue(selection?.name == "acme.avc.encoder")
    }

    private fun candidate(
        name: String,
        colorFormat: Int,
        hardware: Boolean? = null,
        softwareOnly: Boolean? = null,
    ) = EncoderCandidate(
        name = name,
        colorFormats = listOf(colorFormat),
        isHardwareAccelerated = hardware,
        isSoftwareOnly = softwareOnly,
    )
}
