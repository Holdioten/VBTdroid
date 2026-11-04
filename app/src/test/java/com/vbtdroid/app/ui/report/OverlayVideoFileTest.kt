package com.vbtdroid.app.ui.report

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Regression: the report plays the cached overlay only when the file is actually readable. */
class OverlayVideoFileTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = createTempDirectory("overlay-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun nullOrBlankPathIsUnavailable() {
        assertNull(overlayVideoFile(null))
        assertNull(overlayVideoFile(""))
        assertNull(overlayVideoFile("   "))
    }

    @Test
    fun missingFileIsUnavailable() {
        assertNull(overlayVideoFile(File(dir, "missing.mp4").absolutePath))
    }

    @Test
    fun emptyFileIsUnavailable() {
        val empty = File(dir, "empty.mp4").apply { writeBytes(ByteArray(0)) }
        assertNull(overlayVideoFile(empty.absolutePath))
    }

    @Test
    fun directoryIsUnavailable() {
        assertNull(overlayVideoFile(dir.absolutePath))
    }

    @Test
    fun readableNonEmptyFileIsReturned() {
        val video = File(dir, "overlay.mp4").apply { writeBytes(byteArrayOf(0, 0, 0, 24)) }
        assertEquals(video.absoluteFile, overlayVideoFile(video.absolutePath)?.absoluteFile)
    }
}
