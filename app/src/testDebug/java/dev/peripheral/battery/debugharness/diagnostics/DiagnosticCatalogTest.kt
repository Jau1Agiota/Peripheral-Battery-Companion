package dev.peripheral.battery.debugharness.diagnostics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DiagnosticCatalogTest {
    private fun bytes(path: String) = requireNotNull(javaClass.getResourceAsStream("/$path")).use { it.readBytes() }
    private val fakePlatform = object : DiagnosticPlatform {
        override fun debugAssetsAccessible() = true
        override fun activityDeclarationsIsolated() = true
        override fun noInternetOrStoragePermissions() = true
        override fun deviceMetadataAvailable() = true
        override fun offMainThread() = true
    }
    @Test fun fullCatalogActuallyExercisesCoreAndModelsWithoutClaimingDeviceExecution() = runBlocking {
        val catalog = DiagnosticCatalog(DiagnosticAssets(::bytes), fakePlatform).cases()
        assertEquals(17, catalog.count { it.id.startsWith("parser.golden.") || it.id.startsWith("parser.rejected.") })
        assertEquals(20, catalog.count { it.category == DiagnosticCategory.HASH })
        assertEquals(catalog.size, catalog.map { it.id }.distinct().size)
        val run = DiagnosticRunner(catalog).run {}
        assertEquals(RunPhase.COMPLETED, run.phase)
        assertEquals(emptyList<DiagnosticResult>(), run.results.filter { it.status == DiagnosticStatus.FAIL })
        assertEquals(4, run.skipped); assertEquals(catalog.size - 4, run.passed)
        assertTrue(run.results.filter { it.status == DiagnosticStatus.SKIP }.all { it.actual == Observation.NOT_RUN })
        val text = DiagnosticReport.text(run, DiagnosticMetadata("0.1.0-demo", 0, "JVM test double", "JVM VALIDATION"))
        assertTrue(text.contains("JVM VALIDATION")); assertFalse(text.contains("Execution: AUTOMATED ON-DEVICE"))
    }
    @Test fun changedAssetBytesAreDetectedRatherThanHardcodedPasses() = runBlocking {
        val changed = DiagnosticAssets { path -> if (path == "contracts/golden/demo.json") bytes(path) + " ".toByteArray() else bytes(path) }
        val result = DiagnosticRunner(DiagnosticCatalog(changed, fakePlatform).cases()).run {}
        assertTrue(result.results.any { it.id == "hash.golden.demo.json" && it.status == DiagnosticStatus.FAIL })
        assertTrue(result.results.any { it.id == "parser.golden.demo" && it.status == DiagnosticStatus.PASS })
    }
    @Test fun missingAssetsBecomeFailuresWithoutCrashingSuite() = runBlocking {
        val result = DiagnosticRunner(DiagnosticCatalog(DiagnosticAssets { throw java.io.IOException("sensitive path") }, fakePlatform).cases()).run {}
        assertTrue(result.failed > 0); assertEquals(RunPhase.COMPLETED, result.phase)
        assertFalse(DiagnosticReport.text(result, DiagnosticMetadata("0.1.0-demo", 0, "JVM", "JVM VALIDATION")).contains("sensitive path"))
    }
    @Test fun unavailableManualInputsDoNotPreventOptInOrMasqueradeAsValidFixtures() {
        val inputs = dev.peripheral.battery.debugharness.HarnessInputs(
            dev.peripheral.battery.debugharness.HarnessFixture.entries.associateWith { ByteArray(0) })
        val model = dev.peripheral.battery.debugharness.DebugHarnessModel(inputs)
        assertEquals(dev.peripheral.battery.debugharness.HarnessRoute.LANDING, model.state.value.route)
        model.enterOffline(); model.advanceOffline()
        assertTrue(model.state.value.outcome.contains("REJECTED"))
    }
    @Test fun stableCatalogOrderAndIdsAcrossRuns() {
        val one = DiagnosticCatalog(DiagnosticAssets(::bytes), fakePlatform).cases()
        val two = DiagnosticCatalog(DiagnosticAssets(::bytes), fakePlatform).cases()
        assertEquals(one.map { it.id }, two.map { it.id })
        assertEquals(one.map { it.expected }, two.map { it.expected })
    }
}
