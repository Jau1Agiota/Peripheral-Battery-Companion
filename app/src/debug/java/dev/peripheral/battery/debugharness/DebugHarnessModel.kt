package dev.peripheral.battery.debugharness

import androidx.lifecycle.ViewModel
import dev.peripheral.battery.contract.*
import dev.peripheral.battery.domain.*
import dev.peripheral.battery.mocksession.*
import dev.peripheral.battery.simulation.*
import dev.peripheral.battery.ui.forUi
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HarnessFixture(val assetPath: String) {
    DEMO("contracts/golden/demo.json"), ZERO("contracts/golden/zero-and-unknown.json"),
    STALE("contracts/golden/stale-last-known.json"), UNAVAILABLE("contracts/golden/unavailable.json"),
    INVALID("contracts/rejected/version.json"), ONE("simulation/revision-one.json"),
    TWO("simulation/revision-two.json"), UNSUPPORTED("simulation/not-supported.json"),
}
class HarnessInputs(fixtures: Map<HarnessFixture, ByteArray>) {
    private val bytes = HarnessFixture.entries.associateWith { requireNotNull(fixtures[it]).copyOf() }
    fun read(fixture: HarnessFixture) = bytes.getValue(fixture).copyOf()
}
enum class HarnessRoute { LANDING, OFFLINE, SESSION }
enum class HarnessAction(val label: String) {
    BEGIN("Begin mock attempt"), AUTHENTICATE("Approve fake session"), BIND("Bind test publisher"),
    ONE("Receive revision 1"), TWO("Receive revision 2"), DUPLICATE("Repeat revision 2"),
    REPLAY("Reject revision rollback"), ZERO("Receive zero / unknown"), STALE("Receive stale last-known"),
    UNAVAILABLE("Receive all unavailable"), UNSUPPORTED("Receive unsupported"),
    INVALID("Reject invalid fixture"), SYNTHETIC("Reject synthetic live input"),
    CANCEL("Cancel mock session"), EXPIRE("Advance test deadline"), REVOKE("Revoke fake trust"),
    STOP("Stop mock engine"), LATE("Deliver old callback"),
}
data class HarnessCard(val name: String, val current: String, val availability: String, val retained: String, val source: String, val times: String) {
    val accessibilityLabel: String get() = "$name. $current. $availability. $retained. $source. $times. MOCK OFFLINE."
}
data class HarnessUiState(
    val route: HarnessRoute,
    val status: String,
    val outcome: String,
    val publication: String,
    val cards: List<HarnessCard>,
) {
    val warning = "MOCK / OFFLINE · No real PC connection or authentication"
}

/** Debug source set only. Never selected by MainActivity or a release provider. No jobs/persistence. */
class DebugHarnessModel(private val inputs: HarnessInputs) : ViewModel() {
    private val peer = MockPeerIdentity("debug-test-pc", 1)
    private val publisher = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private var simulation = newSimulation()
    private var policy = newPolicy()
    private var handle: MockSessionHandle? = null
    private var oldHandle: MockSessionHandle? = null
    private var generation: Long? = null
    private var cleared = false
    private var ticks = 0L
    private var route = HarnessRoute.LANDING
    private var actionResult = "Explicit opt-in required"
    private val mutable = MutableStateFlow(render())
    val state = mutable.asStateFlow()

    fun enterOffline() { if (cleared) return; if (route == HarnessRoute.SESSION) pause(); route = HarnessRoute.OFFLINE; actionResult = "Local simulation ready"; publish() }
    fun enterSession() { if (cleared) return; route = HarnessRoute.SESSION; actionResult = "Inert fake session ready"; publish() }
    fun advanceOffline() {
        if (cleared || route != HarnessRoute.OFFLINE) return
        simulation.advance(); publish()
    }
    fun reset() {
        if (cleared) return
        oldHandle = handle ?: oldHandle
        policy.stop(); handle = null; generation = null
        ticks = 0; policy = newPolicy(); simulation = newSimulation()
        actionResult = "Reset · Fresh local engines; no persisted trust"; publish()
    }
    fun exit() { reset(); route = HarnessRoute.LANDING; actionResult = "Exited to normal demo"; publish() }
    /** Pause on background, but not during configuration recreation; no automatic resume/reconnect. */
    fun pause() {
        if (cleared) return
        oldHandle = handle ?: oldHandle; policy.cancel(); handle = null; generation = null
        actionResult = "Paused · Explicit begin required for mock session"; publish()
    }
    fun act(action: HarnessAction) {
        if (cleared || route != HarnessRoute.SESSION) return
        ticks++
        val tick = MockTick(ticks)
        when (action) {
            HarnessAction.BEGIN -> {
                oldHandle = handle ?: oldHandle; handle = null
                generation = policy.begin(peer, tick)
                actionResult = if (generation == null) "Begin denied" else "Mock attempt begun"
            }
            HarnessAction.AUTHENTICATE -> {
                handle = generation?.let { policy.authenticate(it, peer, tick) }
                actionResult = if (handle == null) "Fake authentication denied / begin required" else "Fake seam approved · Not real authentication"
            }
            HarnessAction.BIND -> withHandle { policy.bindInstance(it, publisher, tick) }
            HarnessAction.ONE -> withHandle { policy.receive(it, inputs.read(HarnessFixture.ONE), tick) }
            HarnessAction.TWO, HarnessAction.DUPLICATE -> withHandle { policy.receive(it, inputs.read(HarnessFixture.TWO), tick) }
            HarnessAction.REPLAY -> withHandle { policy.receive(it, inputs.read(HarnessFixture.ONE), tick) }
            HarnessAction.INVALID -> withHandle { policy.receive(it, inputs.read(HarnessFixture.INVALID), tick) }
            HarnessAction.SYNTHETIC -> withHandle { policy.receive(it, inputs.read(HarnessFixture.DEMO), tick) }
            HarnessAction.ZERO -> sample(HarnessFixture.ZERO, tick)
            HarnessAction.STALE -> sample(HarnessFixture.STALE, tick)
            HarnessAction.UNAVAILABLE -> sample(HarnessFixture.UNAVAILABLE, tick)
            HarnessAction.UNSUPPORTED -> sample(HarnessFixture.UNSUPPORTED, tick)
            HarnessAction.CANCEL -> { oldHandle = handle ?: oldHandle; policy.cancel(); handle = null; generation = null; actionResult = "Canceled mock session" }
            HarnessAction.EXPIRE -> { ticks += 100; actionResult = policy.checkDeadline(MockTick(ticks)).name }
            HarnessAction.REVOKE -> { oldHandle = handle ?: oldHandle; policy.revokeTrust(); handle = null; generation = null; actionResult = "Fake trust revoked" }
            HarnessAction.STOP -> { oldHandle = handle ?: oldHandle; policy.stop(); handle = null; generation = null; actionResult = "Mock engine stopped" }
            HarnessAction.LATE -> {
                actionResult = oldHandle?.let { policy.receive(it, inputs.read(HarnessFixture.TWO), tick).name } ?: "No old callback · Cancel or stop first"
            }
        }
        publish()
    }
    private fun withHandle(action: (MockSessionHandle) -> MockOutcome) {
        actionResult = handle?.let { action(it).name } ?: "No fake handle · Begin and approve first"
    }
    private fun sample(fixture: HarnessFixture, tick: MockTick) {
        val revision = (policy.state.value.lastAccepted?.evidence?.revision ?: 0) + 1
        // Noncanonical local simulation input only; original canonical bytes are never changed.
        val bytes = inputs.read(fixture).toString(Charsets.UTF_8)
            .replace("\"synthetic\": true", "\"synthetic\": false")
            .replace("\"revision\": 0", "\"revision\": $revision").toByteArray()
        withHandle { policy.receive(it, bytes, tick) }
    }
    private fun newPolicy() = MockSessionPolicy(object : MockAuthenticationTestSeam {
        override fun authorizes(identity: MockPeerIdentity, scope: String) = identity == peer && scope == "snapshot.read"
        override fun confirmsUncachedInstance(identity: MockPeerIdentity, publisher: UUID) = identity == peer && publisher == this@DebugHarnessModel.publisher
    }, deadlineTicks = 100)
    private fun newSimulation() = OfflineSnapshotSimulation(listOf(
        SimulationStep(SimulationScenario.SYNTHETIC_DEMO, inputs.read(HarnessFixture.DEMO)),
        SimulationStep(SimulationScenario.ZERO_VERSUS_NULL, inputs.read(HarnessFixture.ZERO)),
        SimulationStep(SimulationScenario.STALE_LAST_KNOWN, inputs.read(HarnessFixture.STALE)),
        SimulationStep(SimulationScenario.ALL_UNAVAILABLE, inputs.read(HarnessFixture.UNAVAILABLE)),
        SimulationStep(SimulationScenario.INVALID_PAYLOAD, inputs.read(HarnessFixture.INVALID)),
    ))
    private fun publish() { mutable.value = render() }
    private fun render(): HarnessUiState {
        val offline = route == HarnessRoute.OFFLINE
        val s = simulation.state.value
        val m = policy.state.value
        val evidence = if (offline) s.latestAccepted?.evidence else m.lastAccepted?.evidence
        val display = if (offline) s.latestAccepted?.snapshot?.forUi() else m.display.forUi()
        val status = if (offline) "OFFLINE simulation · ${s.scenario ?: "IDLE"} · ${s.outcome}"
            else "MOCK PC session · ${m.phase} · No real PC connected"
        val outcome = if (offline) "Simulation outcome: ${s.outcome} / ${s.error ?: "NONE"}" else "$actionResult · Policy: ${m.outcome} / ${m.denial ?: "NONE"}"
        val publication = evidence?.let { "Retained wire evidence · revision ${it.revision} · synthetic=${it.synthetic} · capturedAt=${it.capturedAt ?: "unknown"}" } ?: "No accepted evidence"
        val cards = DeviceIdentity.entries.mapIndexed { i, identity ->
            val d = display?.devices?.getOrNull(i)
            val wire = evidence?.devices?.getOrNull(i)
            val value = if (d?.batteryPercent == null) "unknown / hidden" else when (d.precision) {
                Precision.PRECISE -> "${d.batteryPercent}%"
                Precision.APPROXIMATE -> "~${d.batteryPercent}%"
                Precision.ESTIMATE -> "Estimate ${d.batteryPercent}%"
                Precision.UNKNOWN -> "unknown confidence (wire ${d.batteryPercent}%)"
            }
            HarnessCard(identity.displayName, "Displayed current: $value",
                "Peripheral wire availability: ${wire?.availability ?: "UNKNOWN"}; freshness=${wire?.freshness ?: "UNKNOWN"}; deviceConnection=${wire?.deviceConnection ?: "UNKNOWN"}; reason=${wire?.unavailableReason ?: "NONE"}",
                "Retained evidence only · wire current=${wire?.current?.batteryPercent ?: "unknown"}; lastKnown=${wire?.lastKnown?.batteryPercent ?: "unknown"} (never current)",
                "Source=${wire?.current?.source?.kind ?: "UNKNOWN"}; precision=${wire?.current?.precision ?: "UNKNOWN"}; socSupport=${wire?.current?.source?.socSupport ?: "UNKNOWN"}; ownership=${wire?.current?.source?.ownership ?: "UNKNOWN"}; lastKnown source=${wire?.lastKnown?.source?.kind ?: "UNKNOWN"}; lastKnown precision=${wire?.lastKnown?.precision ?: "UNKNOWN"}; lastKnown ownership=${wire?.lastKnown?.source?.ownership ?: "UNKNOWN"}",
                "Supplied times only · readAt=${wire?.current?.readAt ?: "unknown"}; observedAt=${wire?.current?.observedAt ?: "unknown"}; lastKnown readAt=${wire?.lastKnown?.readAt ?: "unknown"}; lastKnown observedAt=${wire?.lastKnown?.observedAt ?: "unknown"}")
        }
        return HarnessUiState(route, status, outcome, publication, Collections.unmodifiableList(cards))
    }
    override fun onCleared() {
        cleared = true; policy.stop(); handle = null; generation = null
        actionResult = "Harness ViewModel cleared · No callbacks accepted"; publish()
        super.onCleared()
    }
}
