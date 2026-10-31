package dev.peripheral.battery.simulation

import dev.peripheral.battery.contract.BatterySnapshotV1
import dev.peripheral.battery.contract.OfflineSnapshotProjection
import dev.peripheral.battery.contract.projectOffline
import dev.peripheral.battery.data.DisconnectedBatteryRepository
import dev.peripheral.battery.domain.BatteryRepository
import dev.peripheral.battery.domain.BatterySnapshot
import java.util.Collections
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.*

/** Names describe local simulation inputs, never production telemetry or a PC session. */
enum class SimulationScenario {
    SYNTHETIC_DEMO, ZERO_VERSUS_NULL, STALE_LAST_KNOWN, ALL_UNAVAILABLE,
    NOT_SUPPORTED, INVALID_PAYLOAD, REVISION_ONE, REVISION_TWO, REVISION_REPLAY, INSTANCE_CHANGE,
}

/** Copies caller-owned bytes. No loader, file access, clock, transport or implicit advancement. */
class SimulationStep(val scenario: SimulationScenario, payload: ByteArray) {
    private val bytes = payload.copyOf()
    internal fun parse(): BatterySnapshotV1 = BatterySnapshotV1.parse(bytes)
}

enum class SimulationOutcome { IDLE, ACCEPTED, REJECTED, COMPLETE }
enum class SimulationError { INVALID_PAYLOAD }

data class SimulationState(
    val stepIndex: Int = -1,
    val scenario: SimulationScenario? = null,
    val latestAccepted: OfflineSnapshotProjection? = null,
    val outcome: SimulationOutcome = SimulationOutcome.IDLE,
    val error: SimulationError? = null,
)

/**
 * Explicit offline replay. Every accepted step uses the strict wire parser.
 * Revision is evidence only: no monotonicity/instance-continuity policy or live session is inferred.
 * state is the sole stored observable; snapshots is only a derived compatibility view.
 */
class OfflineSnapshotSimulation(steps: List<SimulationStep>) : BatteryRepository {
    private val sequence = steps.toList()
    private val mutableState = MutableStateFlow(SimulationState())
    val state: StateFlow<SimulationState> = mutableState.asStateFlow()
    private val empty = DisconnectedBatteryRepository().snapshot().let {
        it.copy(devices = Collections.unmodifiableList(ArrayList(it.devices)))
    }
    override val snapshots: StateFlow<BatterySnapshot> = SnapshotView(state, empty)

    /** A rejected step is consumed, but the accepted projection is retained by identity. */
    @Synchronized fun advance(): SimulationState {
        val previous = state.value
        if (previous.outcome == SimulationOutcome.COMPLETE) return previous
        val index = previous.stepIndex + 1
        if (index >= sequence.size) {
            return previous.copy(outcome = SimulationOutcome.COMPLETE, error = null).also { mutableState.value = it }
        }
        val step = sequence[index]
        val next = try {
            val projection = step.parse().projectOffline()
            val immutable = projection.copy(snapshot = projection.snapshot.copy(
                devices = Collections.unmodifiableList(ArrayList(projection.snapshot.devices)),
            ))
            SimulationState(index, step.scenario, immutable, SimulationOutcome.ACCEPTED)
        } catch (_: IllegalArgumentException) {
            // Do not expose exception text, raw input, identities, or timestamp fields as diagnostics.
            SimulationState(index, step.scenario, previous.latestAccepted,
                SimulationOutcome.REJECTED, SimulationError.INVALID_PAYLOAD)
        }
        mutableState.value = next
        return next
    }

    /** Explicit replay reset discards the local accepted projection; it does not start a session. */
    @Synchronized fun reset() { mutableState.value = SimulationState() }
}

/** No second MutableStateFlow, coroutine scope, job, scheduler, or cached copy of simulation state. */
private class SnapshotView(
    private val source: StateFlow<SimulationState>,
    private val empty: BatterySnapshot,
) : StateFlow<BatterySnapshot> {
    override val value: BatterySnapshot get() = source.value.latestAccepted?.snapshot ?: empty
    override val replayCache: List<BatterySnapshot> get() = listOf(value)

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun collect(collector: FlowCollector<BatterySnapshot>): Nothing {
        source.map { it.latestAccepted?.snapshot ?: empty }.distinctUntilChanged().collect(collector)
        error("StateFlow collection cannot complete")
    }
}
