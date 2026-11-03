package com.vbtdroid.core.validation

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.system.exitProcess

object AgreementCsv {
    /** Compares the requested metric, matching records by their `rep` value. */
    fun compareCsv(groundTruthCsv: Path, appCsv: Path, metricColumn: String): AgreementResult {
        require(metricColumn.isNotBlank()) { "Metric column must not be blank" }
        require(metricColumn != "rep") { "Metric column must not be 'rep'" }
        val reference = readMetric(groundTruthCsv, metricColumn)
        val measured = readMetric(appCsv, metricColumn)
        val commonReps = reference.keys.intersect(measured.keys).sorted()
        val pairedReference = mutableListOf<Double>()
        val pairedMeasured = mutableListOf<Double>()
        for (rep in commonReps) {
            val referenceValue = reference.getValue(rep) ?: continue
            val measuredValue = measured.getValue(rep) ?: continue
            pairedReference += referenceValue
            pairedMeasured += measuredValue
        }
        require(pairedReference.size >= 2) { "At least two non-empty paired rows are required" }
        return Agreement.blandAltman(pairedReference.toDoubleArray(), pairedMeasured.toDoubleArray())
    }

    /** Gradle runnable: `./gradlew :core:run --args='truth.csv app.csv mcv_mps'`. */
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size != 3) {
            System.err.println("Usage: <ground_truth.csv> <app.csv> <metric_column>")
            exitProcess(2)
        }
        try {
            val result = compareCsv(Path.of(args[0]), Path.of(args[1]), args[2])
            println("bias=${result.bias.format6()}")
            println("sdDiff=${result.sdDiff.format6()}")
            println("lowerLoa=${result.lowerLoa.format6()}")
            println("upperLoa=${result.upperLoa.format6()}")
            println("icc=${result.icc.format6()}")
        } catch (exception: Exception) {
            System.err.println("CSV agreement failed: ${exception.message}")
            exitProcess(2)
        }
    }

    private fun Double.format6(): String = String.format(Locale.US, "%.6f", this)

    private fun readMetric(path: Path, metricColumn: String): Map<String, Double?> {
        val rows = Files.newBufferedReader(path).use { it.readCsvRows() }
            .filter { row -> row.any { it.isNotBlank() } }
        require(rows.isNotEmpty()) { "CSV is empty: $path" }

        val headers = rows.first().map(String::trim)
        val repIndex = headers.indexOf("rep")
        val metricIndex = headers.indexOf(metricColumn)
        require(repIndex >= 0) { "CSV missing required 'rep' column: $path" }
        require(metricIndex >= 0) { "CSV missing '$metricColumn' column: $path" }
        require(headers.count { it == "rep" } == 1 && headers.count { it == metricColumn } == 1) {
            "CSV headers must contain unique 'rep' and '$metricColumn' columns: $path"
        }

        val values = linkedMapOf<String, Double?>()
        rows.drop(1).forEachIndexed { offset, row ->
            val lineNumber = offset + 2
            require(row.size <= headers.size) { "Too many fields on CSV row $lineNumber: $path" }
            val rep = row.getOrNull(repIndex)?.trim().orEmpty()
            if (rep.isEmpty()) return@forEachIndexed
            require(!values.containsKey(rep)) { "Duplicate rep '$rep' on CSV: $path" }

            val metricText = row.getOrNull(metricIndex)?.trim().orEmpty()
            val metric = if (metricText.isEmpty()) null else {
                metricText.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Invalid '$metricColumn' value on row $lineNumber: $path")
            }
            require(metric == null || metric.isFinite()) {
                "Non-finite '$metricColumn' value on row $lineNumber: $path"
            }
            values[rep] = metric
        }
        return values
    }

    /** Minimal RFC 4180 row reader, including escaped quotes and embedded line breaks. */
    private fun java.io.Reader.readCsvRows(): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var afterQuote = false
        while (true) {
            val charCode = read()
            if (charCode == -1) break
            val char = charCode.toChar()
            when {
                quoted && char == '"' -> {
                    if (markSupported()) {
                        mark(1)
                        if (read() == '"'.code) field.append('"') else {
                            reset()
                            quoted = false
                            afterQuote = true
                        }
                    } else {
                        quoted = false
                        afterQuote = true
                    }
                }
                quoted -> field.append(char)
                afterQuote && char == ',' -> {
                    row += field.toString()
                    field.setLength(0)
                    afterQuote = false
                }
                afterQuote && char == '\n' -> {
                    row += field.toString()
                    rows += row.toList()
                    row.clear()
                    field.setLength(0)
                    afterQuote = false
                }
                afterQuote && char == '\r' -> Unit
                afterQuote -> throw IllegalArgumentException("Unexpected character after closing CSV quote")
                char == '"' && field.isEmpty() -> quoted = true
                char == ',' -> {
                    row += field.toString()
                    field.setLength(0)
                }
                char == '\n' -> {
                    row += field.toString()
                    rows += row.toList()
                    row.clear()
                    field.setLength(0)
                }
                char == '\r' -> Unit
                else -> field.append(char)
            }
        }
        require(!quoted) { "Unterminated quoted CSV field" }
        if (field.isNotEmpty() || row.isNotEmpty() || afterQuote) {
            row += field.toString()
            rows += row.toList()
        }
        return rows
    }
}
