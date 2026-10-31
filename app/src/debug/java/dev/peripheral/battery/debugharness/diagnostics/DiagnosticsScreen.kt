package dev.peripheral.battery.debugharness.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun DiagnosticsScreen(run: DiagnosticRun, exportStatus: String, start: () -> Unit, cancel: () -> Unit,
    back: () -> Unit, copy: () -> Unit, export: () -> Unit) {
    // UI-only elapsed indicator; never advances any parser/simulation/mock-session state.
    var observedMs by remember(run.started, run.phase) { mutableLongStateOf(run.elapsedMs) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(run.phase, run.elapsedMs, lifecycle) {
        observedMs = run.elapsedMs
        if (run.phase == RunPhase.RUNNING) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val began = System.nanoTime()
                while (true) { delay(250); observedMs = run.elapsedMs + (System.nanoTime() - began) / 1_000_000 }
            }
        }
    }
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.safeDrawingPadding().padding(12.dp).testTag("diagnostics-list"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("MOCK / OFFLINE · Automated local diagnostics", style = MaterialTheme.typography.titleLarge)
                Text("No real PC connection or authentication · Gate E BLOCKED")
                Text("Instrumentation NOT RUN · Manual visual checks NOT RUN · Real Windows connection NOT TESTED")
                Text("Finite suite: parser, fixture hashes, simulator, mock session and model checks. Work starts only on your tap. No time estimate; elapsed time below is measured.")
                Button(onClick = back, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Back to manual harness") }
            }
            item {
                Text("${run.phase} · ${run.results.size}/${run.total} checks · elapsed ${observedMs} ms", Modifier.testTag("diagnostic-progress"))
                if (run.phase == RunPhase.RUNNING) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Current check: ${run.current ?: "Preparing catalog"}")
                    Button(onClick = cancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel diagnostics") }
                } else {
                    Text(DiagnosticReport.summary(run), Modifier.testTag("diagnostic-summary").semantics { liveRegion = LiveRegionMode.Polite })
                    Button(onClick = start, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Run All Tests") }
                    OutlinedButton(onClick = copy, enabled = run.terminal, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Copy Summary") }
                    OutlinedButton(onClick = export, enabled = run.terminal, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Export Report (TXT)") }
                }
                Text(exportStatus, Modifier.testTag("export-status"))
            }
            items(run.results, key = { it.id }) { result ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${result.status} · ${result.id}")
                        Text("${result.category} · expected ${result.expected} · actual ${result.actual}")
                        Text("${result.durationMs} ms · ${result.code}")
                    }
                }
            }
        }
    }
}
