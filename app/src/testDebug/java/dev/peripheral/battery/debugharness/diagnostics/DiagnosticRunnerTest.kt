package dev.peripheral.battery.debugharness.diagnostics

import dev.peripheral.battery.data.DemoBatteryRepository
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

class DiagnosticRunnerTest {
    private class Clock : DiagnosticClock {
        private var tick = 0L
        override fun elapsedMs() = tick++
        override fun utc() = "2026-10-31T12:00:00Z"
    }
    private fun good(id: String = "test.good") = DiagnosticCase(id, DiagnosticCategory.EVIDENCE, Observation.ENFORCED) { Observation.ENFORCED }
    private fun metadata() = DiagnosticMetadata("0.1.0-demo", 35, "Test model", "JVM VALIDATION")
    private fun terminal() = runBlocking { DiagnosticRunner(listOf(good()), Clock()).run {} }
    @Test fun stableOrderExpectedActualDurationsAndAggregation() = runBlocking {
        val calls = mutableListOf<String>(); val publications = mutableListOf<DiagnosticRun>()
        val cases = listOf(
            DiagnosticCase("a.first", DiagnosticCategory.PARSER, Observation.ACCEPTED) { calls.add("a"); Observation.ACCEPTED },
            DiagnosticCase("b.fail", DiagnosticCategory.PARSER, Observation.REJECTED) { calls.add("b"); Observation.ACCEPTED },
            DiagnosticCase("c.skip", DiagnosticCategory.NOT_EXECUTED, Observation.ENFORCED, DiagnosticCode.MANUAL_NOT_RUN) { error("Must not run") },
        )
        val end = DiagnosticRunner(cases, Clock()).run { publications.add(it) }
        assertEquals(listOf("a", "b"), calls)
        assertEquals(cases.map { it.id }, end.results.map { it.id })
        assertEquals(1, end.passed); assertEquals(1, end.failed); assertEquals(1, end.skipped)
        assertEquals(RunPhase.COMPLETED, end.phase)
        assertTrue(end.results.all { it.durationMs >= 0 }); assertTrue(end.elapsedMs >= 0)
        assertEquals("a.first", publications.first { it.current != null }.current)
        assertEquals(DiagnosticCode.EXPECTATION_MISMATCH, end.results[1].code)
    }
    @Test fun unexpectedExceptionAndAssertionAreSafeFailuresAndDoNotAbort() = runBlocking {
        val cases = listOf(
            DiagnosticCase("bad.exception", DiagnosticCategory.PARSER, Observation.ACCEPTED) { throw IOException("secret-token-user-path") },
            DiagnosticCase("bad.assertion", DiagnosticCategory.PARSER, Observation.ACCEPTED) { throw AssertionError("private network") }, good(),
        )
        val end = DiagnosticRunner(cases, Clock()).run {}
        assertEquals(2, end.failed); assertEquals(1, end.passed)
        val text = DiagnosticReport.text(end, metadata())
        assertFalse(text.contains("secret-token-user-path")); assertFalse(text.contains("private network"))
        assertTrue(text.contains("UNEXPECTED_EXCEPTION"))
    }
    @Test fun cancellationMarksUnexecutedChecksAndPreservesCompletedResults() = runBlocking {
        val gate = CompletableDeferred<Unit>(); var last = DiagnosticRun()
        val runner = DiagnosticRunner(listOf(good(), DiagnosticCase("wait.case", DiagnosticCategory.PARSER, Observation.ACCEPTED) { gate.complete(Unit); awaitCancellation() }, good("last.case")), Clock())
        val job = launch { runner.run { last = it } }
        gate.await(); job.cancelAndJoin()
        assertEquals(RunPhase.CANCELED, last.phase)
        assertEquals(1, last.passed); assertEquals(2, last.skipped)
        assertTrue(last.results.drop(1).all { it.code == DiagnosticCode.USER_CANCELED && it.actual == Observation.NOT_RUN })
    }
    @Test fun checkTimeoutIsFailureAndLaterChecksStillRun() = runBlocking {
        val runner = DiagnosticRunner(listOf(DiagnosticCase("wait.case", DiagnosticCategory.PARSER, Observation.ACCEPTED) { awaitCancellation() }, good()), Clock(), checkTimeoutMs = 20)
        val end = runner.run {}
        assertEquals(1, end.failed); assertEquals(1, end.passed)
        assertEquals(DiagnosticCode.CHECK_TIMEOUT, end.results[0].code)
    }
    @Test fun suiteTimeoutDoesNotClaimPendingChecksPassed() = runBlocking {
        val end = DiagnosticRunner(listOf(DiagnosticCase("wait.case", DiagnosticCategory.PARSER, Observation.ACCEPTED) { awaitCancellation() }, good()), Clock(), suiteTimeoutMs = 20, checkTimeoutMs = 1000).run {}
        assertEquals(RunPhase.CANCELED, end.phase); assertEquals(0, end.passed)
        assertEquals(2, end.skipped); assertTrue(end.results.all { it.code == DiagnosticCode.SUITE_TIMEOUT })
    }
    @Test fun publishedResultsAreDefensivelyImmutable() {
        val end = terminal()
        assertThrows(UnsupportedOperationException::class.java) { (end.results as MutableList).clear() }
    }
    @Test fun catalogBoundsAndUniqueIdsAreRequired() {
        assertThrows(IllegalArgumentException::class.java) { DiagnosticRunner(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { DiagnosticRunner(listOf(good(), good())) }
        assertThrows(IllegalArgumentException::class.java) { DiagnosticRunner(List(129) { good("test.$it") }) }
    }
    @Test fun reportLabelsExecutionAndUnrunScopesSeparately() {
        val text = DiagnosticReport.text(terminal(), metadata())
        assertTrue(text.contains("Execution: JVM VALIDATION"))
        assertTrue(text.contains("App version: 0.1.0-demo; variant: debug"))
        assertTrue(text.contains("Instrumentation NOT RUN")); assertTrue(text.contains("Real Windows", ignoreCase = true))
        assertTrue(text.contains("manual visual checks NOT RUN"))
        assertTrue(text.contains("source commit: unavailable")); assertTrue(text.contains("variant: debug"))
        assertTrue(text.contains("expected=ENFORCED | actual=ENFORCED | PASS"))
    }
    @Test fun reportRedactsUnsafeMetadataAndNeverExportsExceptionMessages() {
        val text = DiagnosticReport.text(terminal(), DiagnosticMetadata("password=private", 35, "ghp_secret_token", "user@example.com /home/private"))
        assertTrue(text.contains("REDACTED")); assertFalse(text.contains("ghp_")); assertFalse(text.contains("example.com")); assertFalse(text.contains("/home/"))
        assertFalse(text.contains("password=private"))
    }
    @Test fun reportRejectsUnfinishedRunsAndMalformedDates() {
        assertThrows(IllegalArgumentException::class.java) { DiagnosticReport.text(DiagnosticRun(), metadata()) }
        val text = DiagnosticReport.text(terminal().copy(started = "secret/address"), metadata())
        assertFalse(text.contains("secret/address")); assertTrue(text.contains("Start UTC: unavailable"))
    }
    @Test fun successfulExportWritesExactUtf8AndClosesStream() {
        var closed = false
        val bytes = object : ByteArrayOutputStream() { override fun close() { closed = true; super.close() } }
        val text = DiagnosticReport.text(terminal(), metadata())
        assertEquals(ExportResult.WRITTEN, DiagnosticReport.write(text) { bytes })
        assertTrue(closed); assertEquals(text, bytes.toString("UTF-8"))
    }
    @Test fun nullOpenWriteFlushAndCloseFailuresNeverClaimSuccess() {
        assertEquals(ExportResult.FAILED, DiagnosticReport.write("test") { null })
        assertEquals(ExportResult.FAILED, DiagnosticReport.write("test") { throw SecurityException("sensitive uri") })
        for (which in listOf("write", "flush", "close")) {
            val stream = object : java.io.OutputStream() {
                override fun write(b: Int) { if (which == "write") throw IOException("private") }
                override fun flush() { if (which == "flush") throw IOException("private") }
                override fun close() { if (which == "close") throw IOException("private") }
            }
            assertEquals(ExportResult.FAILED, DiagnosticReport.write("test") { stream })
        }
    }
    @Test fun exportCancellationIsPropagatedAndSizeIsBounded() {
        assertThrows(CancellationException::class.java) { DiagnosticReport.write("test") { throw CancellationException("cancel") } }
        assertEquals(ExportResult.FAILED, DiagnosticReport.write("x".repeat(131073)) { error("Must not open") })
    }
    @Test fun modelIsIdleUntilTapAndCannotStartTwoConcurrentRuns() = runBlocking {
        var calls = 0; val gate = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val model = DiagnosticsModel({ calls++; listOf(DiagnosticCase("wait.case", DiagnosticCategory.PARSER, Observation.ACCEPTED) { gate.complete(Unit); release.await(); Observation.ACCEPTED }) }, metadata(), Dispatchers.Unconfined, scope, Clock())
        try {
            assertEquals(RunPhase.IDLE, model.state.value.phase); assertFalse(model.showing.value); assertEquals(0, calls)
            model.start(); gate.await(); model.start(); assertEquals(1, calls)
            release.complete(Unit); yield()
            assertEquals(RunPhase.COMPLETED, model.state.value.phase)
        } finally { scope.cancel() }
    }
    @Test fun backCancelsAndExplicitRerunRecoversWithoutChangingDemo() = runBlocking {
        val before = DemoBatteryRepository().snapshot()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var block = true
        val model = DiagnosticsModel({ listOf(DiagnosticCase("single.case", DiagnosticCategory.PARSER, Observation.ACCEPTED) { if (block) awaitCancellation() else Observation.ACCEPTED }) }, metadata(), Dispatchers.Unconfined, scope, Clock())
        try {
            model.start(); model.back(); yield()
            assertEquals(RunPhase.CANCELED, model.state.value.phase); assertFalse(model.showing.value)
            block = false; model.start(); yield()
            assertEquals(RunPhase.COMPLETED, model.state.value.phase); assertEquals(1, model.state.value.passed)
            assertEquals(before, DemoBatteryRepository().snapshot())
        } finally { scope.cancel() }
    }
    @Test fun exportSnapshotIsStableAcrossLaterRunsAndCancelHasNoSuccess() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val model = DiagnosticsModel({ listOf(good()) }, metadata(), Dispatchers.Unconfined, scope, Clock())
        try {
            assertFalse(model.prepareExport()); assertNull(model.summary())
            model.start(); yield(); assertTrue(model.prepareExport())
            model.start(); yield()
            assertNotNull(model.takePendingExport()); assertNull(model.takePendingExport())
            model.prepareExport(); model.exportCanceled()
            assertNull(model.takePendingExport()); assertTrue(model.exportStatus.value.contains("canceled"))
            model.exportFinished(ExportResult.FAILED); assertFalse(model.exportStatus.value.contains("successfully"))
            model.exportFinished(ExportResult.WRITTEN); assertTrue(model.exportStatus.value.contains("successfully"))
        } finally { scope.cancel() }
    }
    @Test fun cancelBeforeWorkerStartsNeverLeavesRunningState() {
        val queue = java.util.ArrayDeque<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { queue.add(block) }
        }
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val model = DiagnosticsModel({ listOf(good()) }, metadata(), dispatcher, scope, Clock())
        try {
            model.start(); model.cancel()
            while (queue.isNotEmpty()) queue.removeFirst().run()
            assertEquals(RunPhase.CANCELED, model.state.value.phase)
            assertEquals(0, model.state.value.passed)
        } finally { scope.cancel() }
    }
    @Test fun brokenCatalogBecomesSanitizedFailureRatherThanCrash() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val model = DiagnosticsModel({ error("private setup error") }, metadata(), Dispatchers.Unconfined, scope, Clock())
        try {
            model.start(); yield()
            assertEquals(RunPhase.COMPLETED, model.state.value.phase); assertEquals(1, model.state.value.failed)
            assertFalse(DiagnosticReport.text(model.state.value, metadata()).contains("private setup error"))
        } finally { scope.cancel() }
    }
}
