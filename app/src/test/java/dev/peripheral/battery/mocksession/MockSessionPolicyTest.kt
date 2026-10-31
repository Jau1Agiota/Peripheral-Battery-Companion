package dev.peripheral.battery.mocksession

import dev.peripheral.battery.contract.*
import dev.peripheral.battery.domain.*
import dev.peripheral.battery.ui.forUi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MockSessionPolicyTest {
    private val peer = MockPeerIdentity("test-pc", 1)
    private val instance = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val otherInstance = UUID.fromString("00000000-0000-4000-8000-000000000002")
    private class Fake : MockAuthenticationTestSeam {
        var allow = true
        var bind = true
        var scope: String? = null
        var action: (() -> Unit)? = null
        override fun authorizes(identity: MockPeerIdentity, scope: String): Boolean {
            this.scope = scope; action?.invoke(); return allow
        }
        override fun confirmsUncachedInstance(identity: MockPeerIdentity, publisher: UUID) = bind
    }
    private fun resource(path: String) = requireNotNull(javaClass.getResourceAsStream("/$path")).use { it.readBytes() }
    private fun payload(revision: Long = 1, publisher: UUID = instance, golden: String = "demo"): ByteArray =
        resource("contracts/golden/$golden.json").toString(Charsets.UTF_8)
            .replace("\"synthetic\": true", "\"synthetic\": false")
            .replace("\"revision\": 0", "\"revision\": $revision")
            .replace(instance.toString(), publisher.toString()).toByteArray()
    private fun tick(n: Long) = MockTick(n)
    private fun ready(p: MockSessionPolicy, at: Long = 0, identity: MockPeerIdentity = peer, publisher: UUID = instance): MockSessionHandle {
        val generation = p.begin(identity, tick(at))!!
        val h = p.authenticate(generation, identity, tick(at))!!
        assertEquals(MockOutcome.INSTANCE_BOUND, p.bindInstance(h, publisher, tick(at)))
        return h
    }
    private fun hidden(p: MockSessionPolicy) { assertTrue(p.state.value.display.devices.all { it.batteryPercent == null }); assertFalse(p.state.value.display.pcOnline) }

    @Test fun initialAndAuthenticationStatesNeverClaimConnectedFromParsing() {
        val fake = Fake(); val p = MockSessionPolicy(fake, 10)
        assertEquals(MockPhase.DISCONNECTED, p.state.value.phase); hidden(p)
        val parsed = BatterySnapshotV1.parse(payload())
        assertFalse(parsed.synthetic); assertEquals(MockPhase.DISCONNECTED, p.state.value.phase)
        val g = p.begin(peer, tick(0))!!
        assertEquals(MockPhase.CONNECTING, p.state.value.phase); hidden(p)
        val h = p.authenticate(g, peer, tick(1))!!
        assertEquals("snapshot.read", fake.scope)
        assertEquals(MockPhase.AWAITING_RESPONSE, p.state.value.phase); hidden(p)
        p.bindInstance(h, instance, tick(1))
        assertEquals(MockOutcome.ACCEPTED, p.receive(h, payload(), tick(2)))
        assertEquals(MockPhase.CONNECTED, p.state.value.phase)
        assertTrue(p.state.value.mockOnly)
        assertTrue(p.state.value.display.connection.message!!.contains("MOCK ONLY"))
        assertEquals(ConnectionState.DISCONNECTED, p.state.value.lastAccepted!!.snapshot.connection.state)
    }
    @Test fun deniedFakeAuthorizationAndWrongIdentityFailClosed() {
        val fake = Fake(); val p = MockSessionPolicy(fake, 10)
        val g = p.begin(peer, tick(0))!!
        assertNull(p.authenticate(g, peer.copy(identityGeneration = 2), tick(0)))
        assertEquals(MockDenial.WRONG_IDENTITY, p.state.value.denial); hidden(p)
        fake.allow = false
        val next = p.begin(peer, tick(1))!!
        assertNull(p.authenticate(next, peer, tick(1)))
        assertEquals(MockDenial.UNAUTHORIZED, p.state.value.denial); hidden(p)
    }
    @Test fun payloadCannotBindItsOwnPublisher() {
        val p = MockSessionPolicy(Fake(), 10)
        val g = p.begin(peer, tick(0))!!
        val h = p.authenticate(g, peer, tick(0))!!
        assertEquals(MockOutcome.DENIED, p.receive(h, payload(), tick(0)))
        assertEquals(MockDenial.UNBOUND_INSTANCE, p.state.value.denial)
    }
    @Test fun unapprovedBindingAndSyntheticInputAreDenied() {
        val fake = Fake(); val p = MockSessionPolicy(fake, 10)
        val h = ready(p)
        fake.bind = false
        p.bindInstance(h, otherInstance, tick(1))
        assertEquals(MockDenial.UNBOUND_INSTANCE, p.state.value.denial)
        fake.bind = true
        val next = ready(p, 2)
        p.receive(next, resource("contracts/golden/demo.json"), tick(2))
        assertEquals(MockDenial.SYNTHETIC_PAYLOAD, p.state.value.denial); hidden(p)
    }
    @Test fun advancingAndDuplicateRevisionsDoNotRefreshOnDuplicate() {
        val p = MockSessionPolicy(Fake(), 10); val h = ready(p)
        p.receive(h, payload(), tick(1))
        val original = p.state.value.lastAccepted
        assertEquals(MockOutcome.DUPLICATE, p.receive(h, payload(), tick(8)))
        assertSame(original, p.state.value.lastAccepted)
        p.checkDeadline(tick(11))
        assertEquals(MockDenial.DEADLINE, p.state.value.denial); hidden(p)
    }
    @Test fun greaterRevisionAdvancesPublicationAndDeadline() {
        val p = MockSessionPolicy(Fake(), 10); val h = ready(p)
        p.receive(h, payload(), tick(1)); p.receive(h, payload(2), tick(8))
        assertEquals(2L, p.state.value.lastAccepted!!.evidence.revision)
        assertEquals(MockOutcome.NONE, p.checkDeadline(tick(11)))
        assertEquals(MockPhase.CONNECTED, p.state.value.phase)
        p.checkDeadline(tick(18)); hidden(p)
    }
    @Test fun modifiedEqualRevisionIsAnIntegrityFailure() {
        val p = MockSessionPolicy(Fake(), 10); val h = ready(p)
        p.receive(h, payload(), tick(0)); val old = p.state.value.lastAccepted
        val changed = payload().toString(Charsets.UTF_8).replace("\"batteryPercent\": 99", "\"batteryPercent\": 98").toByteArray()
        p.receive(h, changed, tick(1))
        assertEquals(MockDenial.MODIFIED_DUPLICATE, p.state.value.denial)
        assertSame(old, p.state.value.lastAccepted); hidden(p)
    }
    @Test fun lowerRevisionAndReconnectCannotResetHighWater() {
        val p = MockSessionPolicy(Fake(), 10); val h = ready(p)
        p.receive(h, payload(5), tick(0)); p.cancel()
        val next = ready(p, 1)
        p.receive(next, payload(4), tick(2))
        assertEquals(MockDenial.REPLAY_REGRESSION, p.state.value.denial)
        assertEquals(5L, p.state.value.lastAccepted!!.evidence.revision); hidden(p)
    }
    @Test fun duplicateOnReconnectDoesNotEstablishFirstValidNewPublication() {
        val p = MockSessionPolicy(Fake(), 10); val h = ready(p)
        p.receive(h, payload(), tick(0)); p.cancel()
        val next = ready(p, 1)
        assertEquals(MockOutcome.DUPLICATE, p.receive(next, payload(), tick(1)))
        assertEquals(MockPhase.AWAITING_RESPONSE, p.state.value.phase); hidden(p)
        p.receive(next, payload(2), tick(2))
        assertEquals(MockPhase.CONNECTED, p.state.value.phase)
    }
    @Test fun explicitNewInstanceBindingRetiresOldInstance() {
        val p = MockSessionPolicy(Fake(), 10); val h = ready(p)
        p.receive(h, payload(9), tick(0))
        p.bindInstance(h, otherInstance, tick(1)); hidden(p)
        assertEquals(MockOutcome.ACCEPTED, p.receive(h, payload(0, otherInstance), tick(1)))
        assertEquals(otherInstance, p.state.value.lastAccepted!!.evidence.serverInstanceId)
        p.bindInstance(h, instance, tick(2))
        assertEquals(MockDenial.RETIRED_INSTANCE, p.state.value.denial); hidden(p)
    }
    @Test fun instanceChangeWithoutAuthenticatedRebindIsRejected() {
        val p = MockSessionPolicy(Fake(), 10); val h = ready(p)
        p.receive(h, payload(publisher = otherInstance), tick(1))
        assertEquals(MockDenial.UNBOUND_INSTANCE, p.state.value.denial)
    }
    @Test fun boundedHistoryFailsClosedWithoutEvictingOldScopes() {
        val p = MockSessionPolicy(Fake(), 10, historyCapacity = 1); val h = ready(p)
        p.receive(h, payload(), tick(0))
        p.bindInstance(h, otherInstance, tick(1))
        assertEquals(MockDenial.HISTORY_FULL, p.state.value.denial)
        val next = ready(p, 2)
        assertEquals(MockOutcome.DUPLICATE, p.receive(next, payload(), tick(2)))
    }
    @Test fun replayIsScopedToIdentityAndIdentityGeneration() {
        val p = MockSessionPolicy(Fake(), 10); var h = ready(p)
        p.receive(h, payload(10), tick(0))
        h = ready(p, 1, peer.copy(identityGeneration = 2))
        assertEquals(peer, p.state.value.lastAcceptedIdentity)
        assertEquals(peer.copy(identityGeneration = 2), p.state.value.expectedIdentity)
        hidden(p)
        assertEquals(MockOutcome.ACCEPTED, p.receive(h, payload(1), tick(1)))
        h = ready(p, 2, MockPeerIdentity("other-test-pc", 1))
        assertEquals(MockOutcome.ACCEPTED, p.receive(h, payload(0), tick(2)))
        h = ready(p, 3)
        p.receive(h, payload(9), tick(3))
        assertEquals(MockDenial.REPLAY_REGRESSION, p.state.value.denial)
    }
    @Test fun canceledAndForeignEngineHandlesCannotPublish() {
        val p = MockSessionPolicy(Fake(), 10); val old = ready(p)
        p.cancel(); val next = ready(p, 1)
        val before = p.state.value
        assertEquals(MockOutcome.IGNORED_CALLBACK, p.receive(old, payload(), tick(2)))
        assertSame(before, p.state.value)
        val other = MockSessionPolicy(Fake(), 10); val foreign = ready(other)
        assertEquals(MockOutcome.IGNORED_CALLBACK, p.receive(foreign, payload(), tick(2)))
        assertEquals(MockOutcome.ACCEPTED, p.receive(next, payload(), tick(2)))
    }
    @Test fun terminalStopFencesAllLateCallbacksAndCannotRestart() {
        val p = MockSessionPolicy(Fake(), 10); val h = ready(p)
        p.receive(h, payload(), tick(1)); val evidence = p.state.value.lastAccepted
        p.stop(); val stopped = p.state.value
        assertNull(p.begin(peer, tick(2)))
        assertNull(p.authenticate(stopped.localGeneration, peer, tick(2)))
        assertEquals(MockOutcome.IGNORED_CALLBACK, p.receive(h, payload(2), tick(2)))
        assertEquals(MockOutcome.IGNORED_CALLBACK, p.bindInstance(h, otherInstance, tick(2)))
        assertEquals(MockOutcome.IGNORED_CALLBACK, p.checkDeadline(tick(2)))
        assertSame(stopped, p.state.value); assertSame(evidence, stopped.lastAccepted); hidden(p)
    }
    @Test fun invalidResponsePreservesEvidenceButHidesDisplay() {
        val p = MockSessionPolicy(Fake(), 10); val h = ready(p)
        p.receive(h, payload(), tick(0)); val old = p.state.value.lastAccepted
        p.receive(h, "private-invalid-input".toByteArray(), tick(1))
        assertEquals(MockDenial.INVALID_PAYLOAD, p.state.value.denial)
        assertSame(old, p.state.value.lastAccepted); hidden(p)
        assertFalse(p.state.value.toString().contains("private-invalid-input"))
    }
    @Test fun uncertainBackwardAndExpiredTicksDenyWithoutWallClock() {
        listOf(MockTick(1, false), tick(-1), tick(0)).forEach { invalid ->
            val p = MockSessionPolicy(Fake(), 10); val h = ready(p, 1)
            p.receive(h, payload(), invalid)
            assertEquals(MockDenial.CLOCK_UNCERTAIN, p.state.value.denial); hidden(p)
        }
        val p = MockSessionPolicy(Fake(), 10)
        val g = p.begin(peer, tick(0))!!
        assertNull(p.authenticate(g, peer, tick(10)))
        assertEquals(MockDenial.DEADLINE, p.state.value.denial)
        assertNull(MockSessionPolicy(Fake(), 10).begin(peer, tick(Long.MAX_VALUE)))
    }
    @Test fun retentionIsDeniedByDefaultAndOptInStaleNeverSurvivesTrustLoss() {
        val p = MockSessionPolicy(Fake(), 10); val h = ready(p)
        p.receive(h, payload(), tick(0)); p.cancel(); hidden(p)
        assertNotNull(p.state.value.lastAccepted)
        val stale = MockSessionPolicy(Fake(), 10, MockRetention.ALLOW_STALE_AFTER_LOSS)
        val sh = ready(stale); stale.receive(sh, payload(), tick(0)); stale.checkDeadline(tick(10))
        assertEquals(MockPhase.STALE, stale.state.value.phase)
        assertEquals(99, stale.state.value.display.devices[0].batteryPercent)
        assertFalse(stale.state.value.display.pcOnline)
        assertTrue(stale.state.value.display.devices.all { it.availability == Availability.STALE })
        stale.revokeTrust(); assertEquals(MockDenial.TRUST_REVOKED, stale.state.value.denial); hidden(stale)
    }
    @Test fun revokedAuthorizationIsRecheckedBeforeDelivery() {
        val fake = Fake(); val p = MockSessionPolicy(fake, 10); val h = ready(p)
        p.receive(h, payload(), tick(0)); fake.allow = false
        p.receive(h, payload(2), tick(1))
        assertEquals(MockDenial.UNAUTHORIZED, p.state.value.denial)
        assertEquals(1L, p.state.value.lastAccepted!!.evidence.revision); hidden(p)
    }
    @Test fun nullZeroUnavailableAndHyperxEvidenceRemainDistinctFromSession() {
        listOf("zero-and-unknown", "unavailable", "stale-last-known").forEach { name ->
            val p = MockSessionPolicy(Fake(), 10); val h = ready(p)
            p.receive(h, payload(golden = name), tick(0))
            assertEquals(MockPhase.CONNECTED, p.state.value.phase)
            val e = p.state.value.lastAccepted!!.evidence
            val d = p.state.value.display.forUi()
            if (name == "zero-and-unknown") {
                assertEquals(0, d.devices[0].batteryPercent); assertNull(d.devices[1].batteryPercent)
                assertEquals(Precision.ESTIMATE, d.devices[2].precision)
                assertEquals(Ownership.AMBIGUOUS, e.devices[2].current.source.ownership)
            }
            if (name == "unavailable") assertTrue(d.devices.all { it.batteryPercent == null })
            if (name == "stale-last-known") {
                assertNull(d.devices[0].batteryPercent); assertEquals(99, e.devices[0].lastKnown!!.batteryPercent)
            }
        }
    }
    @Test fun oneImmutableCoherentFlowPublishesEvidenceAndPhaseTogether() {
        val p = MockSessionPolicy(Fake(), 10)
        val seen = mutableListOf<MockSessionState>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            scope.launch(start = CoroutineStart.UNDISPATCHED) { p.state.collect { seen.add(it) } }
            val h = ready(p); p.receive(h, payload(), tick(1)); p.receive(h, payload(2), tick(2)); p.cancel()
            assertEquals(7, seen.size)
            seen.forEach {
                assertEquals(it.phase == MockPhase.CONNECTED, it.display.pcOnline)
                if (it.phase == MockPhase.CONNECTED) assertNotNull(it.lastAccepted)
            }
            val accepted = seen.first { it.phase == MockPhase.CONNECTED }.lastAccepted!!
            assertEquals(1L, accepted.evidence.revision)
            assertThrows(UnsupportedOperationException::class.java) { (accepted.snapshot.devices as MutableList).clear() }
            assertThrows(UnsupportedOperationException::class.java) { (seen.last().display.devices as MutableList).clear() }
        } finally { scope.cancel() }
    }
    @Test fun stopDuringDeniedAuthenticationStaysTerminal() {
        val fake = Fake(); val p = MockSessionPolicy(fake, 10)
        val g = p.begin(peer, tick(0))!!
        fake.allow = false; fake.action = { p.stop() }
        assertNull(p.authenticate(g, peer, tick(0)))
        assertEquals(MockPhase.STOPPED, p.state.value.phase)
        assertEquals(MockDenial.STOPPED, p.state.value.denial)
    }
    @Test fun oversizedInputIsRejectedWithoutReplacingAcceptedEvidence() {
        val p = MockSessionPolicy(Fake(), 10); val h = ready(p)
        p.receive(h, payload(), tick(0)); val old = p.state.value.lastAccepted
        p.receive(h, ByteArray(65537), tick(1))
        assertEquals(MockDenial.INVALID_PAYLOAD, p.state.value.denial)
        assertSame(old, p.state.value.lastAccepted); hidden(p)
    }
    @Test fun unsupportedDoesNotImplyPcOrPeripheralDisconnect() {
        val p = MockSessionPolicy(Fake(), 10); val h = ready(p)
        val b = resource("simulation/not-supported.json").toString(Charsets.UTF_8)
            .replace("\"synthetic\": true", "\"synthetic\": false").toByteArray()
        p.receive(h, b, tick(0))
        assertEquals(MockPhase.CONNECTED, p.state.value.phase)
        assertEquals(WireAvailability.NOT_SUPPORTED, p.state.value.lastAccepted!!.evidence.devices[0].availability)
        assertNull(p.state.value.display.devices[0].batteryPercent)
        assertNotEquals(Availability.DEVICE_DISCONNECTED, p.state.value.display.devices[0].availability)
    }
    @Test fun acceptedInputBytesAreCopiedForReplayComparison() {
        val p = MockSessionPolicy(Fake(), 10); val h = ready(p)
        val b = payload()
        p.receive(h, b, tick(0)); b.fill(0)
        assertEquals(MockOutcome.DUPLICATE, p.receive(h, payload(), tick(1)))
        assertEquals(99, p.state.value.lastAccepted!!.evidence.devices[0].current.batteryPercent)
    }
    @Test fun seamFailureOrReentrantStopCannotProduceUsableEvidence() {
        val fake = Fake(); val p = MockSessionPolicy(fake, 10)
        val g = p.begin(peer, tick(0))!!
        fake.action = { p.stop() }
        assertNull(p.authenticate(g, peer, tick(0)))
        assertEquals(MockPhase.STOPPED, p.state.value.phase)
        val errorFake = object : MockAuthenticationTestSeam {
            override fun authorizes(identity: MockPeerIdentity, scope: String): Boolean = error("No raw diagnostic")
            override fun confirmsUncachedInstance(identity: MockPeerIdentity, publisher: UUID) = false
        }
        val denied = MockSessionPolicy(errorFake, 10); val dg = denied.begin(peer, tick(0))!!
        assertNull(denied.authenticate(dg, peer, tick(0)))
        assertEquals(MockDenial.UNAUTHORIZED, denied.state.value.denial)
    }
}
