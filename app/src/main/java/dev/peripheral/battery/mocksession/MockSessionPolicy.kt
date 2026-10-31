package dev.peripheral.battery.mocksession

import dev.peripheral.battery.contract.*
import dev.peripheral.battery.data.DisconnectedBatteryRepository
import dev.peripheral.battery.domain.*
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * INERT mock-only policy, never a transport/provider. All clocks and approvals come from explicit
 * caller actions and the required test seam. No timer, I/O, real credential, key or OS capability.
 */
class MockSessionPolicy(
    private val testSeam: MockAuthenticationTestSeam,
    private val deadlineTicks: Long,
    private val retention: MockRetention = MockRetention.HIDE,
    historyCapacity: Int = 8,
) {
    private val replay = CandidateReplayPolicy(historyCapacity)
    private var generation = 0L
    private var expected: MockPeerIdentity? = null
    private var handle: MockSessionHandle? = null
    private var publisher: UUID? = null
    private var lastTick: Long? = null
    private var deadline = 0L
    private var acceptedIdentity: MockPeerIdentity? = null
    private val mutable = MutableStateFlow(MockSessionState(0, MockPhase.DISCONNECTED, null, hidden(ConnectionState.DISCONNECTED)))
    val state: StateFlow<MockSessionState> = mutable.asStateFlow()
    init { require(deadlineTicks > 0) }

    @Synchronized fun begin(identity: MockPeerIdentity, tick: MockTick): Long? {
        if (state.value.phase == MockPhase.STOPPED) return null
        handle = null; publisher = null
        if (!clock(tick)) return null
        if (generation == Long.MAX_VALUE || tick.value > Long.MAX_VALUE - deadlineTicks) { fail(MockDenial.CLOCK_UNCERTAIN); return null }
        generation++; expected = identity; deadline = tick.value + deadlineTicks
        publish(MockPhase.CONNECTING)
        return generation
    }

    @Synchronized fun authenticate(localGeneration: Long, observedIdentity: MockPeerIdentity, tick: MockTick): MockSessionHandle? {
        if (localGeneration != generation || state.value.phase != MockPhase.CONNECTING) return null
        if (!clock(tick) || !withinDeadline(tick)) return null
        if (observedIdentity != expected) { fail(MockDenial.WRONG_IDENTITY); return null }
        val issued = try { MockSessionHandle.throughTestSeam(testSeam, observedIdentity, generation) } catch (_: Exception) { null }
        if (generation != localGeneration || state.value.phase != MockPhase.CONNECTING) return null
        if (issued == null) { fail(MockDenial.UNAUTHORIZED); return null }
        handle = issued
        publish(MockPhase.AWAITING_RESPONSE, MockOutcome.AUTHENTICATED)
        return issued
    }

    /** Explicit fake authenticated/uncached binding, not inferred from a snapshot UUID. */
    @Synchronized fun bindInstance(evidence: MockSessionHandle, instance: UUID, tick: MockTick): MockOutcome {
        if (!active(evidence)) return MockOutcome.IGNORED_CALLBACK
        if (!authorized(evidence, tick)) return MockOutcome.DENIED
        val approved = try { testSeam.confirmsUncachedInstance(evidence.identity, instance) } catch (_: Exception) { false }
        if (!active(evidence)) return MockOutcome.IGNORED_CALLBACK
        if (!approved || instance == UUID(0, 0)) return fail(MockDenial.UNBOUND_INSTANCE)
        replay.bind(evidence.identity, instance)?.let { return fail(it) }
        publisher = instance
        // A new binding never makes an old accepted publication current or fresh.
        publish(MockPhase.AWAITING_RESPONSE, MockOutcome.INSTANCE_BOUND)
        return MockOutcome.INSTANCE_BOUND
    }

    @Synchronized fun receive(evidence: MockSessionHandle, payload: ByteArray, tick: MockTick): MockOutcome {
        if (!active(evidence)) return MockOutcome.IGNORED_CALLBACK
        if (!authorized(evidence, tick)) return MockOutcome.DENIED
        // One defensive bounded copy is used for both parsing and byte-exact duplicate comparison.
        if (payload.size > BatterySnapshotV1.MAX_PAYLOAD_BYTES) return fail(MockDenial.INVALID_PAYLOAD)
        val bytes = payload.copyOf()
        val parsed = try { BatterySnapshotV1.parse(bytes) } catch (_: IllegalArgumentException) { return fail(MockDenial.INVALID_PAYLOAD) }
        if (parsed.synthetic) return fail(MockDenial.SYNTHETIC_PAYLOAD)
        if (publisher != parsed.serverInstanceId) return fail(MockDenial.UNBOUND_INSTANCE)
        if (tick.value > Long.MAX_VALUE - deadlineTicks) return fail(MockDenial.CLOCK_UNCERTAIN)
        val (outcome, denial) = replay.observe(evidence.identity, parsed.serverInstanceId, parsed.revision, bytes)
        if (denial != null) return fail(denial)
        if (outcome == MockOutcome.DUPLICATE) {
            // A duplicate cannot advance the deadline, receipt freshness, or awaiting-first-response state.
            publish(state.value.phase, MockOutcome.DUPLICATE)
            return outcome
        }
        val p = parsed.projectOffline().let { it.copy(snapshot = it.snapshot.copy(devices = immutable(it.snapshot.devices))) }
        acceptedIdentity = evidence.identity
        deadline = tick.value + deadlineTicks
        publish(MockPhase.CONNECTED, outcome, accepted = p)
        return outcome
    }

    /** Explicit test-clock action only; never schedules work. Exact deadline is expired. */
    @Synchronized fun checkDeadline(tick: MockTick): MockOutcome {
        if (state.value.phase == MockPhase.STOPPED) return MockOutcome.IGNORED_CALLBACK
        if (!clock(tick)) return MockOutcome.DENIED
        if (handle == null && state.value.phase != MockPhase.CONNECTING) return MockOutcome.NONE
        if (tick.value < deadline) return MockOutcome.NONE
        return lose(MockDenial.DEADLINE)
    }
    @Synchronized fun cancel(): MockOutcome = if (state.value.phase == MockPhase.STOPPED) MockOutcome.IGNORED_CALLBACK else lose(MockDenial.CANCELED)
    @Synchronized fun revokeTrust(): MockOutcome = if (state.value.phase == MockPhase.STOPPED) MockOutcome.IGNORED_CALLBACK else fail(MockDenial.TRUST_REVOKED)
    @Synchronized fun stop() {
        handle = null; publisher = null
        publish(MockPhase.STOPPED, MockOutcome.DENIED, MockDenial.STOPPED)
    }

    private fun active(evidence: MockSessionHandle) = state.value.phase != MockPhase.STOPPED && handle === evidence && evidence.localGeneration == generation
    private fun authorized(evidence: MockSessionHandle, tick: MockTick): Boolean {
        if (!clock(tick) || !withinDeadline(tick)) return false
        val allowed = try { testSeam.authorizes(evidence.identity, "snapshot.read") } catch (_: Exception) { false }
        if (!active(evidence)) return false
        if (!allowed) { fail(MockDenial.UNAUTHORIZED); return false }
        return true
    }
    private fun clock(tick: MockTick): Boolean {
        if (!tick.certain || tick.value < 0 || (lastTick != null && tick.value < lastTick!!)) { fail(MockDenial.CLOCK_UNCERTAIN); return false }
        lastTick = tick.value
        return true
    }
    private fun withinDeadline(tick: MockTick): Boolean {
        if (tick.value < deadline) return true
        lose(MockDenial.DEADLINE); return false
    }
    private fun lose(reason: MockDenial): MockOutcome {
        handle = null; publisher = null
        val stale = retention == MockRetention.ALLOW_STALE_AFTER_LOSS && state.value.lastAccepted != null && expected == acceptedIdentity
        publish(if (stale) MockPhase.STALE else MockPhase.DISCONNECTED, MockOutcome.DENIED, reason)
        return MockOutcome.DENIED
    }
    private fun fail(reason: MockDenial): MockOutcome {
        handle = null; publisher = null
        publish(MockPhase.ERROR, MockOutcome.DENIED, reason)
        return MockOutcome.DENIED
    }
    private fun publish(phase: MockPhase, outcome: MockOutcome = MockOutcome.NONE, denial: MockDenial? = null,
                        accepted: OfflineSnapshotProjection? = state.value.lastAccepted) {
        val connection = when (phase) {
            MockPhase.CONNECTING, MockPhase.AWAITING_RESPONSE -> ConnectionState.CONNECTING
            MockPhase.CONNECTED -> ConnectionState.CONNECTED
            MockPhase.STALE -> ConnectionState.STALE
            MockPhase.ERROR -> ConnectionState.ERROR
            else -> ConnectionState.DISCONNECTED
        }
        val show = phase == MockPhase.CONNECTED || phase == MockPhase.STALE
        val display = if (show && accepted != null) accepted.snapshot.copy(
            connection = ConnectionMetadata(connection, "MOCK ONLY · Not real authentication"),
            devices = immutable(accepted.snapshot.devices.map { if (phase == MockPhase.STALE) it.copy(availability = Availability.STALE) else it }),
        ) else hidden(connection)
        mutable.value = MockSessionState(generation, phase, accepted, display, outcome, denial, expected, acceptedIdentity)
    }
    private fun hidden(connection: ConnectionState) = DisconnectedBatteryRepository().snapshot().let {
        it.copy(connection = ConnectionMetadata(connection, "MOCK ONLY · Not real authentication"), devices = immutable(it.devices))
    }
    private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
}
