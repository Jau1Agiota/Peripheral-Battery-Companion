package dev.peripheral.battery.simulation

import dev.peripheral.battery.contract.*
import dev.peripheral.battery.data.DemoBatteryRepository
import dev.peripheral.battery.domain.*
import dev.peripheral.battery.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test

class OfflineSnapshotSimulationTest {
    private fun bytes(path: String) = requireNotNull(javaClass.getResourceAsStream("/$path")).use { it.readBytes() }
    private fun canonical(name: String) = bytes("contracts/golden/$name.json")
    private fun extra(name: String) = bytes("simulation/$name.json")
    private fun step(s: SimulationScenario, b: ByteArray) = SimulationStep(s, b)
    private fun steps() = listOf(
        step(SimulationScenario.SYNTHETIC_DEMO, canonical("demo")),
        step(SimulationScenario.ZERO_VERSUS_NULL, canonical("zero-and-unknown")),
        step(SimulationScenario.STALE_LAST_KNOWN, canonical("stale-last-known")),
        step(SimulationScenario.ALL_UNAVAILABLE, canonical("unavailable")),
        step(SimulationScenario.NOT_SUPPORTED, extra("not-supported")),
        step(SimulationScenario.INVALID_PAYLOAD, bytes("contracts/rejected/version.json")),
        step(SimulationScenario.REVISION_ONE, extra("revision-one")),
        step(SimulationScenario.REVISION_TWO, extra("revision-two")),
    )
    private fun once(s: SimulationScenario, b: ByteArray) = OfflineSnapshotSimulation(listOf(step(s, b))).advance()
    private fun trace(sim: OfflineSnapshotSimulation): List<String> = buildList {
        repeat(steps().size + 1) {
            val s = sim.advance(); val e = s.latestAccepted?.evidence
            add("${s.stepIndex}/${s.scenario}/${s.outcome}/${s.error}/${e?.revision}/${e?.synthetic}/${s.latestAccepted?.snapshot}")
        }
    }
    @Test fun initialStateIsIdleAndDisconnectedWithoutImplicitParsing() {
        val sim = OfflineSnapshotSimulation(steps())
        assertEquals(SimulationState(), sim.state.value)
        assertEquals(ConnectionState.DISCONNECTED, sim.snapshot().connection.state)
        assertTrue(sim.snapshot().devices.all { it.batteryPercent == null })
        assertEquals(sim.snapshot(), sim.snapshots.replayCache.single())
    }
    @Test fun explicitSequenceOrderAndCompletionAreStable() {
        val sim = OfflineSnapshotSimulation(steps())
        steps().forEachIndexed { index, expected ->
            val s = sim.advance()
            assertEquals(index, s.stepIndex); assertEquals(expected.scenario, s.scenario)
            assertEquals(if (expected.scenario == SimulationScenario.INVALID_PAYLOAD) SimulationOutcome.REJECTED else SimulationOutcome.ACCEPTED, s.outcome)
            assertSame(s, sim.state.value)
        }
        val last = sim.state.value.latestAccepted
        val complete = sim.advance()
        assertEquals(SimulationOutcome.COMPLETE, complete.outcome)
        assertSame(last, complete.latestAccepted)
        assertSame(complete, sim.advance())
    }
    @Test fun deterministicReplayAndExplicitReset() {
        val sim = OfflineSnapshotSimulation(steps())
        val first = trace(sim)
        sim.reset()
        assertEquals(SimulationState(), sim.state.value)
        assertFalse(sim.snapshot().pcOnline)
        assertEquals(first, trace(sim))
        assertEquals(first, trace(OfflineSnapshotSimulation(steps())))
    }
    @Test fun emptySequenceCompletesWithoutFabricatedEvidence() {
        val sim = OfflineSnapshotSimulation(emptyList())
        assertEquals(SimulationOutcome.COMPLETE, sim.advance().outcome)
        assertNull(sim.state.value.latestAccepted)
        assertEquals(-1, sim.state.value.stepIndex)
    }
    @Test fun zeroIsPreservedAndNullRemainsUnknown() {
        val s = once(SimulationScenario.ZERO_VERSUS_NULL, canonical("zero-and-unknown")).latestAccepted!!
        assertEquals(0, s.evidence.devices[0].current.batteryPercent)
        assertEquals("0%", s.snapshot.forUi().devices[0].present().value)
        assertNull(s.evidence.devices[1].current.batteryPercent)
        assertEquals("Unknown", s.snapshot.forUi().devices[1].present().value)
    }
    @Test fun retainedEvidenceDoesNotBecomeCurrent() {
        val p = once(SimulationScenario.STALE_LAST_KNOWN, canonical("stale-last-known")).latestAccepted!!
        val d = p.evidence.devices[0]
        assertEquals(99, d.lastKnown?.batteryPercent)
        assertNull(d.current.batteryPercent)
        assertEquals(WireAvailability.STALE, d.availability)
        assertEquals(Freshness.STALE, d.freshness)
        assertEquals(UnavailableReason.STALE_CACHE, d.unavailableReason)
        assertEquals(SourceKind.GHUB_GATEWAY, d.lastKnown?.source?.kind)
        assertNull(p.snapshot.devices[0].batteryPercent)
        assertEquals(Availability.STALE, p.snapshot.devices[0].availability)
    }
    @Test fun unavailableAndUnsupportedNeverProveDeviceDisconnect() {
        listOf(canonical("unavailable"), extra("not-supported")).forEach { b ->
            val p = once(SimulationScenario.NOT_SUPPORTED, b).latestAccepted!!
            assertTrue(p.evidence.devices.all { it.current.batteryPercent == null })
            assertTrue(p.snapshot.devices.all { it.availability != Availability.DEVICE_DISCONNECTED })
        }
        val p = once(SimulationScenario.NOT_SUPPORTED, extra("not-supported")).latestAccepted!!
        assertEquals(WireAvailability.NOT_SUPPORTED, p.evidence.devices[0].availability)
        assertEquals(UnavailableReason.UNSUPPORTED, p.evidence.devices[0].unavailableReason)
    }
    @Test fun precisionOwnershipAndReceiptEvidenceAreNotUpgraded() {
        val p = once(SimulationScenario.SYNTHETIC_DEMO, canonical("demo")).latestAccepted!!
        assertEquals(listOf(Precision.PRECISE, Precision.UNKNOWN, Precision.ESTIMATE), p.snapshot.devices.map { it.precision })
        assertEquals("Unknown", p.snapshot.devices[1].present().value)
        val h = p.evidence.devices[2].current
        assertEquals(SourceKind.HYPERX_SUPERVISED, h.source.kind)
        assertEquals(Ownership.AMBIGUOUS, h.source.ownership)
        assertEquals(SocSupport.NOT_APPLICABLE, h.source.socSupport)
        assertNull(h.observedAt)
        assertTrue(p.snapshot.devices[2].caution!!.contains("ambiguous"))
    }
    @Test fun invalidInputRollsBackAndRecoveryClearsError() {
        val sim = OfflineSnapshotSimulation(listOf(steps()[0], steps()[5], steps()[1]))
        val good = sim.advance().latestAccepted
        val bad = sim.advance()
        assertSame(good, bad.latestAccepted)
        assertEquals(SimulationError.INVALID_PAYLOAD, bad.error)
        assertEquals(SimulationOutcome.REJECTED, bad.outcome)
        assertSame(good!!.snapshot, sim.snapshot())
        val recovered = sim.advance()
        assertNull(recovered.error); assertEquals(SimulationOutcome.ACCEPTED, recovered.outcome)
        assertEquals(0, recovered.latestAccepted!!.snapshot.devices[0].batteryPercent)
    }
    @Test fun firstInvalidInputHasNoAcceptedSnapshotAndNoRawDiagnostic() {
        val sim = OfflineSnapshotSimulation(listOf(step(SimulationScenario.INVALID_PAYLOAD, "secret-payload".toByteArray())))
        val bad = sim.advance()
        assertNull(bad.latestAccepted); assertEquals(SimulationError.INVALID_PAYLOAD, bad.error)
        assertFalse(bad.toString().contains("secret-payload"))
        assertEquals(ConnectionState.DISCONNECTED, sim.snapshot().connection.state)
    }
    @Test fun everyCanonicalRejectionUsesRealParserAndRetainsAcceptedState() {
        val scenarios = listOf("demo-time", "duplicate-device", "duplicate-key", "gateway-observation", "hyperx-confidence", "malformed", "missing-field", "null-available", "percent-range", "quoted-percent", "unknown-enum", "unknown-field", "version")
        val sim = OfflineSnapshotSimulation(listOf(steps()[0]) + scenarios.map { step(SimulationScenario.INVALID_PAYLOAD, bytes("contracts/rejected/$it.json")) })
        val original = sim.advance().latestAccepted
        scenarios.forEach { _ ->
            val state = sim.advance()
            assertSame(original, state.latestAccepted)
            assertEquals(SimulationError.INVALID_PAYLOAD, state.error)
        }
    }
    @Test fun revisionProgressionIsEvidenceNotAConnection() {
        val sim = OfflineSnapshotSimulation(steps().takeLast(2))
        val one = sim.advance().latestAccepted!!
        val two = sim.advance().latestAccepted!!
        assertEquals(1L, one.evidence.revision); assertEquals(2L, two.evidence.revision)
        assertEquals(one.evidence.serverInstanceId, two.evidence.serverInstanceId)
        listOf(one, two).forEach {
            assertFalse(it.evidence.synthetic); assertFalse(it.snapshot.synthetic)
            assertEquals(ConnectionState.DISCONNECTED, it.snapshot.connection.state)
            assertFalse(it.snapshot.pcOnline); assertNull(it.evidence.capturedAt)
            assertTrue(it.snapshot.forUi().devices.all { d -> d.batteryPercent == null })
        }
    }
    @Test fun duplicateDecreasingRevisionsAndNewInstancesAreIndependentOfflineImports() {
        val sim = OfflineSnapshotSimulation(listOf(
            step(SimulationScenario.REVISION_TWO, extra("revision-two")),
            step(SimulationScenario.REVISION_REPLAY, extra("revision-two")),
            step(SimulationScenario.REVISION_ONE, extra("revision-one")),
            step(SimulationScenario.INSTANCE_CHANGE, extra("instance-change")),
        ))
        val states = List(4) { sim.advance() }
        assertEquals(listOf(2L, 2L, 1L, 0L), states.map { it.latestAccepted!!.evidence.revision })
        assertTrue(states.all { it.outcome == SimulationOutcome.ACCEPTED && !it.latestAccepted!!.snapshot.pcOnline })
        assertNotEquals(states[0].latestAccepted!!.evidence.serverInstanceId, states[3].latestAccepted!!.evidence.serverInstanceId)
    }
    @Test fun syntheticLabelsAndDefaultDemoRemainExplicit() {
        val p = once(SimulationScenario.SYNTHETIC_DEMO, canonical("demo")).latestAccepted!!
        assertTrue(p.snapshot.synthetic); assertFalse(p.snapshot.pcOnline)
        assertTrue(p.snapshot.connection.present().banner.contains("Synthetic"))
        assertEquals("Demo mode · Not connected", p.snapshot.connection.present().status)
        assertEquals(listOf(99, 90, 76), DemoBatteryRepository().snapshot().devices.map { it.batteryPercent })
        assertNull(p.evidence.capturedAt)
    }
    @Test fun suppliedTimestampsAndDeviceConnectionRemainEvidenceOnly() {
        val supplied = extra("revision-one").toString(Charsets.UTF_8)
            .replace("\"capturedAt\": null", "\"capturedAt\": \"2026-10-31T12:00:00Z\"")
            .replace("\"deviceConnection\": \"UNKNOWN\"", "\"deviceConnection\": \"ACTIVE_REPORTED\"")
        val p = once(SimulationScenario.REVISION_ONE, supplied.toByteArray()).latestAccepted!!
        assertEquals(java.time.Instant.parse("2026-10-31T12:00:00Z"), p.evidence.capturedAt)
        assertEquals(p.evidence.capturedAt, p.snapshot.capturedAt)
        assertTrue(p.evidence.devices.all { it.deviceConnection == DeviceConnection.ACTIVE_REPORTED })
        assertFalse(p.snapshot.pcOnline)
    }
    @Test fun inputsAndPublishedCollectionsAreDefensivelyImmutable() {
        val input = canonical("demo")
        val step = step(SimulationScenario.SYNTHETIC_DEMO, input)
        val steps = mutableListOf(step)
        val sim = OfflineSnapshotSimulation(steps)
        input.fill(0); steps.clear()
        val accepted = sim.advance().latestAccepted!!
        assertEquals(99, accepted.evidence.devices[0].current.batteryPercent)
        assertThrows(UnsupportedOperationException::class.java) { (accepted.evidence.devices as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (accepted.snapshot.devices as MutableList).clear() }
        sim.reset()
        assertEquals(99, accepted.evidence.devices[0].current.batteryPercent)
    }
    @Test fun singleStatePublicationIsCoherentForEveryStep() {
        val sim = OfflineSnapshotSimulation(steps())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val observed = mutableListOf<SimulationState>()
        try {
            scope.launch(start = CoroutineStart.UNDISPATCHED) { sim.state.collect { observed.add(it) } }
            repeat(steps().size) { sim.advance() }
            assertEquals(steps().size + 1, observed.size)
            observed.drop(1).forEach { s ->
                assertEquals(s.outcome == SimulationOutcome.REJECTED, s.error != null)
                if (s.outcome == SimulationOutcome.ACCEPTED) {
                    val expected = steps()[s.stepIndex].parse()
                    assertEquals(expected.revision, s.latestAccepted!!.evidence.revision)
                    assertEquals(expected.synthetic, s.latestAccepted.snapshot.synthetic)
                }
            }
            assertSame(observed[5].latestAccepted, observed[6].latestAccepted)
        } finally { scope.cancel() }
    }
    @Test fun repositoryAndViewModelObserveControlledTransitionsAndCancellation() {
        val sim = OfflineSnapshotSimulation(steps())
        val repository: BatteryRepository = sim
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val vm = BatteryViewModel(repository, scope)
        try {
            assertEquals(ConnectionState.DISCONNECTED, vm.state.value.snapshot.connection.state)
            sim.advance(); assertEquals(99, vm.state.value.snapshot.devices[0].batteryPercent)
            sim.advance(); assertEquals(0, vm.state.value.snapshot.devices[0].batteryPercent)
            sim.advance(); assertNull(vm.state.value.snapshot.devices[0].batteryPercent)
            repeat(2) { sim.advance() }
            val before = vm.state.value
            sim.advance(); assertEquals(before, vm.state.value)
            sim.advance()
            assertEquals(ConnectionState.DISCONNECTED, vm.state.value.snapshot.connection.state)
            assertTrue(vm.state.value.snapshot.devices.all { it.batteryPercent == null })
            assertFalse(vm.state.value.snapshot.pcOnline)
            scope.cancel()
            val stopped = vm.state.value
            sim.reset(); sim.advance(); assertEquals(stopped, vm.state.value)
        } finally { scope.cancel() }
    }
    @Test fun compatibilityFlowSuppressesErrorOnlyUpdatesAndResetIsExplicit() {
        val sim = OfflineSnapshotSimulation(listOf(steps()[0], steps()[5]))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val seen = mutableListOf<BatterySnapshot>()
        try {
            scope.launch(start = CoroutineStart.UNDISPATCHED) { sim.snapshots.collect { seen.add(it) } }
            sim.advance(); sim.advance(); sim.advance()
            assertEquals(2, seen.size)
            sim.refreshDemo(); assertEquals(SimulationOutcome.COMPLETE, sim.state.value.outcome)
            sim.reset(); assertEquals(3, seen.size)
            assertEquals(ConnectionState.DISCONNECTED, seen.last().connection.state)
        } finally { scope.cancel() }
    }
}
