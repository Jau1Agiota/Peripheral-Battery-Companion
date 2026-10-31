package dev.peripheral.battery.debugharness.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Foreground-owned structured work only; no service, persistent job, auto-run or telemetry. */
class DiagnosticsModel(
    private val catalog: () -> List<DiagnosticCase>,
    val metadata: DiagnosticMetadata,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scopeOverride: CoroutineScope? = null,
    private val clock: DiagnosticClock = DeviceDiagnosticClock,
) : ViewModel() {
    private val mutable = MutableStateFlow(DiagnosticRun())
    val state = mutable.asStateFlow()
    private val show = MutableStateFlow(false)
    val showing = show.asStateFlow()
    private var job: Job? = null
    private var cleared = false
    private var runGeneration = 0L
    private var pendingExport: String? = null
    private val exportMessage = MutableStateFlow("No export attempted")
    val exportStatus = exportMessage.asStateFlow()

    fun start() {
        if (cleared || job?.isActive == true) return
        val runId = ++runGeneration
        show.value = true
        mutable.value = DiagnosticRun(phase = RunPhase.RUNNING)
        exportMessage.value = "No export attempted for this run"
        job = (scopeOverride ?: viewModelScope).launch {
            withContext(dispatcher) {
                val runner = try { DiagnosticRunner(catalog(), clock) } catch (e: CancellationException) { throw e } catch (_: Exception) {
                    DiagnosticRunner(listOf(DiagnosticCase("catalog.unavailable", DiagnosticCategory.PARSER, Observation.ENFORCED) { error("Catalog unavailable") }), clock)
                }
                runner.run { if (runId == runGeneration) mutable.value = it }
            }
        }
        job?.invokeOnCompletion { error ->
            if (runId == runGeneration && mutable.value.phase == RunPhase.RUNNING && error is CancellationException) {
                mutable.value = mutable.value.copy(phase = RunPhase.CANCELED, current = null, ended = clock.utc())
            }
        }
    }
    fun cancel() { job?.cancel() }
    fun back() { cancel(); show.value = false }
    fun summary(): String? = state.value.takeIf { it.terminal }?.let(DiagnosticReport::summary)
    fun prepareExport(): Boolean {
        if (!state.value.terminal) return false
        pendingExport = DiagnosticReport.text(state.value, metadata)
        exportMessage.value = "Choose a document destination"
        return true
    }
    fun takePendingExport(): String? = pendingExport.also { pendingExport = null }
    fun exportCanceled() { pendingExport = null; exportMessage.value = "Export canceled · No success claimed" }
    fun exportFinished(result: ExportResult) { exportMessage.value = if (result == ExportResult.WRITTEN) "Report written successfully" else "Export failed · No success claimed" }
    fun exportUnavailable() { exportMessage.value = "Report unavailable after recreation · Run the suite explicitly again" }
    override fun onCleared() { cleared = true; job?.cancel(); pendingExport = null; super.onCleared() }
}
