package dev.peripheral.battery.debugharness.diagnostics

import java.io.OutputStream
import kotlinx.coroutines.CancellationException

data class DiagnosticMetadata(val version: String, val api: Int, val model: String, val environment: String)
enum class ExportResult { WRITTEN, FAILED }

/** Fixed allowlisted fields only. Exception messages, URI/path, serials, payloads and stack traces never enter reports. */
object DiagnosticReport {
    const val SUITE_VERSION = "A2.3F-1"
    private fun safe(value: String, max: Int = 64): String {
        if (value.length !in 1..max || !Regex("[A-Za-z0-9 _-]+").matches(value) ||
            Regex("(?i)(token|secret|password|ghp_|github_pat_|bearer)").containsMatchIn(value)) return "REDACTED"
        return value
    }
    private fun version(value: String) = if (Regex("""[0-9]{1,3}(\.[0-9]{1,3}){1,3}(-[A-Za-z0-9-]{1,24})?""").matches(value)) value else "REDACTED"
    private fun utc(value: String?) = value?.let { runCatching { java.time.Instant.parse(it).toString() }.getOrDefault("unavailable") } ?: "unavailable"
    fun summary(run: DiagnosticRun) = "MOCK OFFLINE diagnostics ${run.phase}: ${run.passed} PASS / ${run.failed} FAIL / ${run.skipped} SKIP of ${run.total}. " +
        "Instrumentation NOT RUN; manual visual checks NOT RUN; real Windows connection NOT TESTED. Gate E BLOCKED."
    fun text(run: DiagnosticRun, metadata: DiagnosticMetadata): String {
        require(run.terminal) { "Only a stable completed/canceled report can be exported" }
        return buildString {
            appendLine("Peripheral Battery Companion - DEBUG MOCK OFFLINE")
            appendLine("Suite: $SUITE_VERSION; source commit: unavailable (not embedded)")
            appendLine("App version: ${version(metadata.version)}; variant: debug")
            appendLine("Execution: ${safe(metadata.environment)}; Android API: ${metadata.api.coerceIn(0, 999)}; model: ${safe(metadata.model)}")
            appendLine("Start UTC: ${utc(run.started)}; end UTC: ${utc(run.ended)}; elapsed ms: ${run.elapsedMs.coerceAtLeast(0)}")
            appendLine(summary(run))
            appendLine("Only listed executed checks have results. No real PC authentication, TLS, pairing, security or physical UI proof.")
            run.results.forEach { result ->
                // IDs are catalog identifiers only, never exception-provided messages or raw paths.
                val id = if (Regex("[a-z0-9][a-z0-9.-]{0,95}").matches(result.id)) result.id else "REDACTED"
                appendLine("$id | ${result.category} | expected=${result.expected} | actual=${result.actual} | ${result.status} | ms=${result.durationMs.coerceAtLeast(0)} | code=${result.code}")
            }
        }
    }
    /** Success means write, flush AND close returned; a null stream or close failure is not success. */
    fun write(text: String, open: () -> OutputStream?): ExportResult = try {
        require(text.toByteArray(Charsets.UTF_8).size <= 131072)
        val stream = open() ?: return ExportResult.FAILED
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)); it.flush() }
        ExportResult.WRITTEN
    } catch (e: CancellationException) { throw e
    } catch (_: Exception) { ExportResult.FAILED }
}
