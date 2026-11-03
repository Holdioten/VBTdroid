package com.vbtdroid.core.validation

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AgreementCsvTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `compares paired metric rows and skips empty values`() {
        val groundTruth = tempDir.resolve("ground-truth.csv")
        val appExport = tempDir.resolve("app.csv")
        Files.writeString(
            groundTruth,
            "rep,rom\n1,0.2\n2,\n3,0.4\n4,0.6\n",
        )
        Files.writeString(
            appExport,
            "rep,rom,vl\n1,0.2,\n2,0.3,\n3,0.5,\n4,0.5,\n",
        )

        val result = AgreementCsv.compareCsv(groundTruth, appExport, "rom")

        assertEquals(0.0, result.bias, 1e-9)
        assertEquals(kotlin.math.sqrt(0.01), result.sdDiff, 1e-9)
        assertEquals(-0.196, result.lowerLoa, 1e-9)
        assertEquals(0.196, result.upperLoa, 1e-9)
    }
}
