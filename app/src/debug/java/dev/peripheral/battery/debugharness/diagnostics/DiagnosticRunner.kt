package dev.peripheral.battery.debugharness.diagnostics

import java.time.Instant
import java.util.Collections
import kotlinx.coroutines.*

enum class DiagnosticCategory { PARSER, HASH, EVIDENCE, SIMULATOR, MOCK_SESSION, PRESENTATION, ANDROID_RUNTIME, NOT_EXECUTED }
enum class Observation { ACCEPTED, REJECTED, MATCH, ENFORCED, NOT_ENFORCED, NOT_RUN, EXCEPTION }
enum class DiagnosticStatus { PASS, FAIL, SKIP }
enum class RunPhase { IDLE, RUNNING, COMPLETED, CANCELED }
enum class DiagnosticCode { NONE, EXPECTATION_MISMATCH, UNEXPECTED_EXCEPTION, CHECK_TIMEOUT, USER_CANCELED, SUITE_TIMEOUT, INSTRUMENTATION_NOT_RUN, MANUAL_NOT_RUN, WINDOWS_NOT_TESTED, RELEASE_NOT_INSPECTED }

class DiagnosticCase(
    val id: String,
    val category: DiagnosticCategory,
    val expected: Observation,
    val skip: DiagnosticCode? = null,
    val check: suspend () -> Observation,
) {
    init { require(Regex("[a-z0-9][a-z0-9.-]{0,95}").matches(id)) }
}
data class DiagnosticResult(val id: String, val category: DiagnosticCategory, val expected: Observation,
    val actual: Observation, val status: DiagnosticStatus, val durationMs: Long, val code: DiagnosticCode)
data class DiagnosticRun(
    val phase: RunPhase = RunPhase.IDLE,
    val total: Int = 0,
    val current: String? = null,
    val results: List<DiagnosticResult> = emptyList(),
    val started: String? = null,
    val ended: String? = null,
    val elapsedMs: Long = 0,
) {
    val passed get() = results.count { it.status == DiagnosticStatus.PASS }
    val failed get() = results.count { it.status == DiagnosticStatus.FAIL }
    val skipped get() = results.count { it.status == DiagnosticStatus.SKIP }
    val terminal get() = phase == RunPhase.COMPLETED || phase == RunPhase.CANCELED
}
interface DiagnosticClock { fun elapsedMs(): Long; fun utc(): String }
object DeviceDiagnosticClock : DiagnosticClock {
    override fun elapsedMs() = System.nanoTime() / 1_000_000
    override fun utc() = Instant.now().toString()
}

/** Sequential finite catalog, cooperative cancellation/time budgets; no retries or I/O logging. */
class DiagnosticRunner(
    cases: List<DiagnosticCase>,
    private val clock: DiagnosticClock = DeviceDiagnosticClock,
    private val suiteTimeoutMs: Long = 60_000,
    private val checkTimeoutMs: Long = 5_000,
) {
    private val catalog = cases.toList()
    init {
        require(catalog.size in 1..128 && catalog.map { it.id }.distinct().size == catalog.size)
        require(suiteTimeoutMs > 0 && checkTimeoutMs > 0)
    }
    suspend fun run(publish: (DiagnosticRun) -> Unit): DiagnosticRun {
        val start = clock.elapsedMs(); val started = clock.utc()
        val results = mutableListOf<DiagnosticResult>()
        fun snapshot(phase: RunPhase, current: String? = null) = DiagnosticRun(phase, catalog.size, current,
            Collections.unmodifiableList(ArrayList(results)), started, if (phase == RunPhase.RUNNING) null else clock.utc(),
            (clock.elapsedMs() - start).coerceAtLeast(0))
        var reason = DiagnosticCode.USER_CANCELED
        publish(snapshot(RunPhase.RUNNING))
        try {
            withTimeout(suiteTimeoutMs) {
                for (case in catalog) {
                    currentCoroutineContext().ensureActive()
                    publish(snapshot(RunPhase.RUNNING, case.id))
                    val before = clock.elapsedMs()
                    var code = case.skip ?: DiagnosticCode.NONE
                    val actual = if (case.skip != null) Observation.NOT_RUN else try {
                        withTimeout(checkTimeoutMs) { case.check() }
                    } catch (e: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive()
                        code = DiagnosticCode.CHECK_TIMEOUT; Observation.EXCEPTION
                    } catch (e: CancellationException) { throw e
                    } catch (_: Exception) {
                        code = DiagnosticCode.UNEXPECTED_EXCEPTION; Observation.EXCEPTION
                    } catch (_: AssertionError) {
                        code = DiagnosticCode.EXPECTATION_MISMATCH; Observation.EXCEPTION
                    }
                    currentCoroutineContext().ensureActive()
                    val status = when {
                        case.skip != null -> DiagnosticStatus.SKIP
                        actual == case.expected && code == DiagnosticCode.NONE -> DiagnosticStatus.PASS
                        else -> DiagnosticStatus.FAIL
                    }
                    if (status == DiagnosticStatus.FAIL && code == DiagnosticCode.NONE) code = DiagnosticCode.EXPECTATION_MISMATCH
                    results.add(DiagnosticResult(case.id, case.category, case.expected, actual, status,
                        (clock.elapsedMs() - before).coerceAtLeast(0), code))
                    publish(snapshot(RunPhase.RUNNING))
                    yield()
                }
            }
        } catch (_: TimeoutCancellationException) {
            reason = DiagnosticCode.SUITE_TIMEOUT
            return canceled(results, reason, ::snapshot, publish)
        } catch (_: CancellationException) {
            canceled(results, reason, ::snapshot, publish)
            throw CancellationException("Diagnostics canceled")
        }
        return snapshot(RunPhase.COMPLETED).also(publish)
    }
    private fun canceled(results: MutableList<DiagnosticResult>, reason: DiagnosticCode,
        snapshot: (RunPhase, String?) -> DiagnosticRun, publish: (DiagnosticRun) -> Unit): DiagnosticRun {
        catalog.drop(results.size).forEach { case ->
            results.add(DiagnosticResult(case.id, case.category, case.expected, Observation.NOT_RUN,
                DiagnosticStatus.SKIP, 0, reason))
        }
        return snapshot(RunPhase.CANCELED, null).also(publish)
    }
}
