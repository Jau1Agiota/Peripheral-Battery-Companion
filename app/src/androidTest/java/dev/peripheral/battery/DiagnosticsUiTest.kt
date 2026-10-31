package dev.peripheral.battery

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.peripheral.battery.debugharness.DebugHarnessActivity
import dev.peripheral.battery.debugharness.diagnostics.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** These tests require a real Android runtime; compiling them does not execute them. */
class DiagnosticsUiTest {
    @get:Rule val compose = createAndroidComposeRule<DebugHarnessActivity>()
    private fun runSuite() {
        compose.onNodeWithTag("diagnostic-progress").assertDoesNotExist()
        compose.onNodeWithText("Run All Tests").performClick()
        compose.onNodeWithTag("diagnostics-list").performScrollToIndex(1)
        compose.waitUntil(60_000) {
            compose.onAllNodesWithTag("diagnostic-summary").fetchSemanticsNodes().any { node ->
                node.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.any { it.text.contains("COMPLETED") }
            }
        }
    }
    @Test fun explicitTapRunsActualOnDeviceCatalogWithHonestScopeLabels() {
        runSuite()
        compose.onNodeWithTag("diagnostic-summary").assertTextContains("0 FAIL", substring = true)
        compose.onNodeWithTag("diagnostic-summary").assertTextContains("4 SKIP", substring = true)
        compose.onNodeWithTag("diagnostic-summary").assertTextContains("Windows connection NOT TESTED", substring = true)
        compose.onNodeWithText("Copy Summary").assertIsEnabled()
        compose.onNodeWithText("Export Report (TXT)").assertIsEnabled()
    }
    @Test fun systemPickerCancellationNeverClaimsExportSuccess() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain")
        assumeTrue("No system document provider", intent.resolveActivity(compose.activity.packageManager) != null)
        runSuite()
        compose.onNodeWithText("Export Report (TXT)").performScrollTo().performClick()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("export-status").fetchSemanticsNodes().any { node ->
                node.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.any { it.text.contains("Export canceled") }
            }
        }
    }
    @Test fun reportWriterRoundTripsUtf8OnAndroidStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = DiagnosticResult("device.writer", DiagnosticCategory.ANDROID_RUNTIME, Observation.ENFORCED,
            Observation.ENFORCED, DiagnosticStatus.PASS, 0, DiagnosticCode.NONE)
        val report = DiagnosticReport.text(DiagnosticRun(RunPhase.COMPLETED, total = 1, results = listOf(result)), AndroidDiagnosticPlatform(context).metadata())
        val file = java.io.File.createTempFile("offline-report-test-", ".txt", context.cacheDir)
        try {
            assertEquals(ExportResult.WRITTEN, DiagnosticReport.write(report) { file.outputStream() })
            assertEquals(report, file.readText(Charsets.UTF_8))
        } finally { assertTrue(file.delete()) }
    }
}
