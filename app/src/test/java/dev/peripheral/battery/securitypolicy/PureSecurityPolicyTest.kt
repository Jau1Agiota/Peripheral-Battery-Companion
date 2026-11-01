package dev.peripheral.battery.securitypolicy

import dev.peripheral.battery.contract.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class PureSecurityPolicyTest {
    private val epoch = Instant.parse("2026-10-31T00:00:00Z")
    // Explicit fake labels, not endpoints, certs, nonces, verifiers or credentials.
    private val binding = Binding("test-endpoint", "test-leaf", "test-spki", "test-enrollment",
        "test-phone", "test-phone-nonce", "test-verifier")
    private fun at(n: Long) = Sample(epoch.plusSeconds(n), n)
    private fun auth(lifetime: Long = 10000) = Authorization(1, epoch, epoch.plusSeconds(lifetime), at(0))
    private fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/interop/e2/$name.json")).use { it.readBytes() }
    // Controlled test-only mutations; original E2 bytes/resources remain unchanged.
    private fun bytes(revision: Long = 1, instance: Int = 1): ByteArray =
        resource("sequence-first").toString(Charsets.UTF_8)
            .replace(Regex("\"revision\":\\s*[0-9]+"), "\"revision\":$revision")
            .replace(Regex("\"serverInstanceId\":\\s*\"[^\"]+\""),
                "\"serverInstanceId\":\"${UUID(0, instance.toLong())}\"").toByteArray()
    private fun ready(p: SessionPolicy, now: Long = 0): Long {
        val g = requireNotNull(p.begin(at(now)))
        assertTrue(p.confirm(g, binding, true, at(now)))
        return g
    }
    private fun deliver(p: SessionPolicy, g: Long, now: Long = 1, payload: ByteArray = bytes(),
                        commit: Commit = Commit.COMMITTED): Outcome {
        assertTrue(p.request(g, "test-request-$now", at(now)))
        return p.receive(g, "test-request-$now", 1, BatterySnapshotV1.parse(payload).serverInstanceId,
            payload, commit, at(now))
    }
    private fun hidden(p: SessionPolicy) { assertNull(p.state.displayed); assertNull(p.state.channelAt) }

    @Test fun expiryBoundaryIsStrictAndCannotRecover() {
        val a = auth(10)
        assertTrue(a.check(at(9)))
        assertFalse(a.check(at(10)))
        assertEquals(Denial.EXPIRED, a.denial)
        assertFalse(a.check(at(9)))
    }
    @Test fun monotonicBudgetExpiresDespiteSlowWallClock() {
        val a = auth(10)
        assertFalse(a.check(Sample(epoch.plusSeconds(1), 10)))
        assertEquals(Denial.EXPIRED, a.denial)
    }
    @Test fun backwardAndUncertainClocksLatchDenial() {
        val variants = listOf(Sample(epoch.minusSeconds(1), 1), Sample(epoch, -1), Sample(epoch, 0, false))
        for (now in variants) {
            val a = auth(); assertFalse(a.check(now)); assertEquals(Denial.CLOCK, a.denial)
            assertFalse(a.check(at(1)))
        }
    }
    @Test fun invalidAuthorizationRecordsCannotBeConstructed() {
        assertThrows(IllegalArgumentException::class.java) { Authorization(0, epoch, epoch.plusSeconds(1), at(0)) }
        assertThrows(IllegalArgumentException::class.java) { Authorization(1, epoch, epoch, at(0)) }
        assertThrows(IllegalArgumentException::class.java) { Authorization(1, epoch.plusSeconds(1), epoch.plusSeconds(10), at(0)) }
    }
    @Test fun revocationDeniesRegardlessOfPersistenceOutcome() {
        for (commit in Commit.entries) {
            val a = auth(); assertEquals(commit == Commit.COMMITTED, a.revoke(commit))
            assertFalse(a.check(at(1)))
            assertEquals(if (commit == Commit.COMMITTED) Denial.REVOKED else Denial.PERSISTENCE, a.denial)
        }
    }
    @Test fun queuedResponseIsDeniedAtExactExpiry() {
        val p = SessionPolicy(auth(10), binding); val g = ready(p)
        assertTrue(p.request(g, "test-request", at(1)))
        assertTrue(p.mayRelease(g, at(9)))
        assertFalse(p.mayRelease(g, at(10))); hidden(p)
        assertEquals(Denial.EXPIRED, p.state.denial)
    }
    @Test fun revokeImmediatelyInvalidatesQueuedAndLateResponse() {
        for (commit in Commit.entries) {
            val p = SessionPolicy(auth(), binding); val g = ready(p)
            assertTrue(p.request(g, "test-request", at(1)))
            assertEquals(commit == Commit.COMMITTED, p.revoke(commit))
            assertFalse(p.mayRelease(g, at(2)))
            assertEquals(Outcome.DENIED, p.receive(g, "test-request", 1, UUID(0, 1), bytes(), Commit.COMMITTED, at(2)))
            assertNull(p.begin(at(3))); hidden(p)
        }
    }
    @Test fun everyTranscriptOrEndpointFieldIsBound() {
        val substitutions = listOf(binding.copy(endpoint = "other-endpoint"), binding.copy(leaf = "other-leaf"),
            binding.copy(spki = "other-spki"), binding.copy(enrollment = "other-enrollment"),
            binding.copy(phone = "other-phone"), binding.copy(phoneNonce = "other-nonce"),
            binding.copy(verifier = "other-verifier"), binding.copy(protocol = 2))
        for (observed in substitutions) {
            val p = SessionPolicy(auth(), binding); val g = p.begin(at(0))!!
            assertFalse(p.confirm(g, observed, true, at(1)))
            assertEquals(Denial.BINDING, p.state.denial); hidden(p)
        }
    }
    @Test fun matchingTranscriptWithoutVerifiedPeerDoesNotAuthorize() {
        val p = SessionPolicy(auth(), binding); val g = p.begin(at(0))!!
        assertFalse(p.confirm(g, binding, false, at(1))); hidden(p)
    }
    @Test fun parsingAndConfirmationAloneDoNotConnect() {
        BatterySnapshotV1.parse(bytes())
        val p = SessionPolicy(auth(), binding)
        assertEquals(Phase.DISCONNECTED, p.state.phase)
        ready(p)
        assertEquals(Phase.AWAITING_RESPONSE, p.state.phase); hidden(p)
    }
    @Test fun duplicateRefreshesChannelButNeverProgressOrEvidence() {
        val p = SessionPolicy(auth(), binding); val g = ready(p)
        assertEquals(Outcome.ADVANCED, deliver(p, g))
        val accepted = p.state.lastAccepted
        assertEquals(Outcome.DUPLICATE, deliver(p, g, 10))
        assertEquals(10L, p.state.channelAt); assertEquals(1L, p.state.progressedAt)
        assertSame(accepted, p.state.lastAccepted); assertSame(accepted, p.state.displayed)
    }
    @Test fun duplicateAfterReconnectEstablishesOnlyChannelLiveness() {
        val p = SessionPolicy(auth(), binding); val g = ready(p)
        deliver(p, g); val accepted = p.state.lastAccepted
        p.disconnect(); hidden(p)
        val next = ready(p, 2)
        assertEquals(Outcome.DUPLICATE, deliver(p, next, 3))
        assertEquals(Phase.CONNECTED, p.state.phase); assertEquals(1L, p.state.progressedAt)
        assertSame(accepted, p.state.lastAccepted)
    }
    @Test fun regressionAndModifiedDuplicateNeverReplaceEvidence() {
        for ((bad, reason) in listOf(bytes(1) to Denial.REGRESSION, (bytes(2) + byteArrayOf(32)) to Denial.MODIFIED_DUPLICATE)) {
            val p = SessionPolicy(auth(), binding); val g = ready(p)
            deliver(p, g, payload = bytes(2)); val accepted = p.state.lastAccepted
            assertEquals(Outcome.DENIED, deliver(p, g, 2, bad))
            assertEquals(reason, p.state.denial)
            assertSame(accepted, p.state.lastAccepted); hidden(p)
            assertNull(p.begin(at(3)))
        }
    }
    @Test fun newerRevisionAdvancesWithoutInventingMeasurementTimes() {
        val p = SessionPolicy(auth(), binding); val g = ready(p)
        deliver(p, g); val original = p.state.lastAccepted!!
        assertEquals(Outcome.ADVANCED, deliver(p, g, 2, bytes(2)))
        assertEquals(original.devices, p.state.lastAccepted!!.devices)
        assertEquals(2L, p.state.progressedAt)
    }
    @Test fun retiredPublisherIsNeverReaccepted() {
        val p = SessionPolicy(auth(), binding); val g = ready(p)
        deliver(p, g)
        assertEquals(Outcome.ADVANCED, deliver(p, g, 2, bytes(instance = 2)))
        assertEquals(Outcome.DENIED, deliver(p, g, 3))
        assertEquals(Denial.RETIRED_INSTANCE, p.state.denial); hidden(p)
    }
    @Test fun sixtyFifthInstanceLatchesRecoveryWithoutEviction() {
        val p = SessionPolicy(auth(), binding); val g = ready(p)
        for (n in 1..64) assertEquals(Outcome.ADVANCED, deliver(p, g, n.toLong(), bytes(instance = n)))
        val accepted = p.state.lastAccepted
        assertEquals(Outcome.DENIED, deliver(p, g, 65, bytes(instance = 65)))
        assertEquals(64, p.historySize); assertEquals(Denial.HISTORY_FULL, p.state.denial)
        assertSame(accepted, p.state.lastAccepted); hidden(p)
        assertNull(p.begin(at(66))); assertFalse(p.request(g, "retry", at(66)))
    }
    @Test fun failedCommitCannotPublishOrBeRetriedInPlace() {
        for (commit in listOf(Commit.NOT_COMMITTED, Commit.UNCERTAIN)) {
            val p = SessionPolicy(auth(), binding); val g = ready(p)
            deliver(p, g); val accepted = p.state.lastAccepted
            assertEquals(Outcome.DENIED, deliver(p, g, 2, bytes(2), commit))
            assertEquals(1, p.historySize); assertSame(accepted, p.state.lastAccepted)
            assertEquals(Denial.PERSISTENCE, p.state.denial); hidden(p)
            assertNull(p.begin(at(3)))
        }
    }
    @Test fun missingOrRollbackStateCannotSilentlyResetLedger() {
        val p = SessionPolicy(auth(), binding); val g = ready(p); deliver(p, g)
        p.uncertainState(); assertNull(p.begin(at(2))); hidden(p)
        assertEquals(1, p.historySize); assertEquals(Denial.PERSISTENCE, p.state.denial)
    }
    @Test fun wrongNonceAuthorizationGenerationAndPublisherFailClosed() {
        for (kind in 0..2) {
            val p = SessionPolicy(auth(), binding); val g = ready(p)
            assertTrue(p.request(g, "expected", at(1)))
            assertEquals(Outcome.DENIED, p.receive(g, if (kind == 0) "wrong" else "expected",
                if (kind == 1) 2 else 1, UUID(0, if (kind == 2) 2 else 1), bytes(), Commit.COMMITTED, at(1)))
            assertEquals(Denial.BINDING, p.state.denial); hidden(p)
        }
    }
    @Test fun nonceReuseAndConcurrentRequestAreRejected() {
        for (duplicatePending in listOf(true, false)) {
            val p = SessionPolicy(auth(), binding); val g = ready(p)
            assertTrue(p.request(g, "one", at(1)))
            if (!duplicatePending) p.receive(g, "one", 1, UUID(0, 1), bytes(), Commit.COMMITTED, at(1))
            assertFalse(p.request(g, if (duplicatePending) "two" else "one", at(2)))
            hidden(p)
        }
    }
    @Test fun backgroundAndReplacementIgnoreOldCallbacksWithoutPoisoningNewState() {
        val p = SessionPolicy(auth(), binding); val old = ready(p)
        assertTrue(p.request(old, "old", at(1)))
        p.disconnect(); hidden(p); val next = ready(p, 2)
        val before = p.state
        assertEquals(Outcome.DENIED, p.receive(old, "old", 1, UUID(0, 1), bytes(), Commit.COMMITTED, at(0)))
        assertSame(before, p.state)
        assertEquals(Outcome.ADVANCED, deliver(p, next, 3))
    }
    @Test fun deadlineIsExactAndDoesNotEraseReplayHistory() {
        val p = SessionPolicy(auth(), binding); val g = ready(p); deliver(p, g)
        assertTrue(p.checkDeadline(at(30)))
        assertFalse(p.checkDeadline(at(31))); hidden(p)
        val next = ready(p, 32)
        assertEquals(Outcome.DUPLICATE, deliver(p, next, 33))
        assertEquals(1, p.historySize)
    }
    @Test fun stopIsTerminalEvenAfterValidResponse() {
        val p = SessionPolicy(auth(), binding); val g = ready(p); deliver(p, g)
        p.stop(); p.disconnect(); assertNull(p.begin(at(2)))
        assertEquals(Phase.STOPPED, p.state.phase); hidden(p)
    }
    @Test fun malformedSyntheticAndZeroRevisionCannotBecomeDisplay() {
        val original = bytes()
        val synthetic = original.toString(Charsets.UTF_8).replace("\"synthetic\":false", "\"synthetic\":true").toByteArray()
        assertFalse(original.contentEquals(synthetic))
        for (bad in listOf(byteArrayOf(-1), synthetic, bytes(0))) {
            val p = SessionPolicy(auth(), binding); val g = ready(p)
            assertTrue(p.request(g, "test", at(1)))
            assertEquals(Outcome.DENIED, p.receive(g, "test", 1, UUID(0, 1), bad, Commit.COMMITTED, at(1)))
            hidden(p)
        }
    }
    @Test fun inputMutationCannotChangeAcceptedReplayBytes() {
        val p = SessionPolicy(auth(), binding); val g = ready(p)
        val original = bytes(); deliver(p, g, payload = original); original.fill(0)
        assertEquals(Outcome.DUPLICATE, deliver(p, g, 2))
    }
    @Test fun exactDeadlineAlsoDeniesConfirmRequestAndRelease() {
        val connecting = SessionPolicy(auth(), binding); val first = connecting.begin(at(0))!!
        assertFalse(connecting.confirm(first, binding, true, at(30)))
        val requesting = SessionPolicy(auth(), binding); val second = ready(requesting)
        assertFalse(requesting.request(second, "late", at(30)))
        val sending = SessionPolicy(auth(), binding); val third = ready(sending)
        assertTrue(sending.request(third, "prepared", at(1)))
        assertFalse(sending.mayRelease(third, at(30)))
        for (p in listOf(connecting, requesting, sending)) {
            assertEquals(Phase.DISCONNECTED, p.state.phase); hidden(p)
        }
    }
    @Test fun expiryAlsoDeniesDuplicatesOnPreviouslyConnectedSession() {
        val p = SessionPolicy(auth(10), binding); val g = ready(p); deliver(p, g)
        assertTrue(p.request(g, "prepared", at(9)))
        assertEquals(Outcome.DENIED, p.receive(g, "prepared", 1, UUID(0, 1), bytes(), Commit.COMMITTED, at(10)))
        assertEquals(Denial.EXPIRED, p.state.denial); hidden(p)
        assertEquals(1L, p.state.lastAccepted!!.revision)
    }
    @Test fun revocationPendingDeniesBeforePersistenceAcknowledgement() {
        val p = SessionPolicy(auth(), binding); val g = ready(p)
        assertTrue(p.request(g, "queued", at(1)))
        p.requestRevocation()
        assertFalse(p.mayRelease(g, at(2))); hidden(p)
        assertFalse(p.finishRevocation(Commit.UNCERTAIN))
        assertEquals(Denial.PERSISTENCE, p.state.denial)
        assertFalse(p.finishRevocation(Commit.COMMITTED))
        assertNull(p.begin(at(3)))
    }
    @Test fun persistenceUncertaintyOnDuplicateAlsoLatchesRecovery() {
        val p = SessionPolicy(auth(), binding); val g = ready(p); deliver(p, g)
        assertEquals(Outcome.DENIED, deliver(p, g, 2, commit = Commit.UNCERTAIN))
        assertEquals(Denial.PERSISTENCE, p.state.denial); hidden(p)
    }
    @Test fun validSyntheticFixtureCannotEnterLivePolicy() {
        val demo = requireNotNull(javaClass.getResourceAsStream("/contracts/golden/demo.json")).use { it.readBytes() }
        val parsed = BatterySnapshotV1.parse(demo); assertTrue(parsed.synthetic)
        val p = SessionPolicy(auth(), binding); val g = ready(p)
        assertTrue(p.request(g, "test-demo", at(1)))
        assertEquals(Outcome.DENIED, p.receive(g, "test-demo", 1, parsed.serverInstanceId, demo, Commit.COMMITTED, at(1)))
        assertEquals(Denial.INPUT, p.state.denial); hidden(p)
    }
    @Test fun revocationAndUncertaintyCannotRestartStoppedLifecycle() {
        val p = SessionPolicy(auth(), binding); ready(p); p.stop()
        p.requestRevocation(); p.finishRevocation(Commit.UNCERTAIN); p.uncertainState()
        assertEquals(Phase.STOPPED, p.state.phase)
        assertNull(p.begin(at(1))); hidden(p)
    }
    private fun timedOut(timeoutFirst: Boolean): SessionPolicy {
        val p = SessionPolicy(auth(), binding); val g = ready(p)
        deliver(p, g) // Retired publisher retained in history.
        deliver(p, g, 2, bytes(5, 2))
        val accepted = p.state.lastAccepted
        assertTrue(p.request(g, "pending-at-deadline", at(31)))
        if (timeoutFirst) assertFalse(p.checkDeadline(at(32)))
        assertEquals(Outcome.DENIED, p.receive(g, "pending-at-deadline", 1, UUID(0, 2),
            bytes(6, 2), Commit.COMMITTED, at(32)))
        // A later timer event is a no-op once transiently disconnected.
        assertTrue(p.checkDeadline(at(32)))
        assertEquals(Phase.DISCONNECTED, p.state.phase)
        assertNull(p.state.denial); hidden(p)
        assertSame(accepted, p.state.lastAccepted)
        assertEquals(2L, p.state.progressedAt); assertEquals(2, p.historySize)
        return p
    }
    private fun reconnectAfterDeadline(timeoutFirst: Boolean) {
        val p = timedOut(timeoutFirst)
        val g = ready(p, 33)
        assertEquals(Outcome.DUPLICATE, deliver(p, g, 34, bytes(5, 2)))
        assertEquals(Phase.CONNECTED, p.state.phase)
        assertEquals(34L, p.state.channelAt); assertEquals(2L, p.state.progressedAt)
        assertEquals(2, p.historySize)
    }
    @Test fun timeoutFirstThenDeadlineResponseAllowsCheckedReconnect() {
        reconnectAfterDeadline(true)
    }
    @Test fun deadlineResponseFirstThenTimeoutAllowsCheckedReconnect() {
        reconnectAfterDeadline(false)
    }
    @Test fun deadlineDoesNotConvertExpiredAuthorizationIntoTransientFailure() {
        for (timeoutFirst in listOf(true, false)) {
            val p = SessionPolicy(auth(31), binding); val g = ready(p); deliver(p, g)
            assertTrue(p.request(g, "pending", at(30)))
            if (timeoutFirst) assertFalse(p.checkDeadline(at(31)))
            assertEquals(Outcome.DENIED, p.receive(g, "pending", 1, UUID(0, 1), bytes(), Commit.COMMITTED, at(31)))
            assertEquals(Phase.ERROR, p.state.phase); assertEquals(Denial.EXPIRED, p.state.denial)
            assertNull(p.begin(at(32))); hidden(p)
        }
    }
    @Test fun reconnectAfterEitherTimeoutOrderPreservesReplayAndIntegrityFailures() {
        val failures = listOf(bytes(4, 2) to Denial.REGRESSION,
            (bytes(5, 2) + byteArrayOf(32)) to Denial.MODIFIED_DUPLICATE,
            bytes() to Denial.RETIRED_INSTANCE)
        for (timeoutFirst in listOf(true, false)) for ((payload, reason) in failures) {
            val p = timedOut(timeoutFirst); val g = ready(p, 33)
            assertEquals(Outcome.DENIED, deliver(p, g, 34, payload))
            assertEquals(reason, p.state.denial); assertEquals(Phase.ERROR, p.state.phase)
            assertNull(p.begin(at(35))); hidden(p)
        }
    }
    @Test fun reconnectAfterEitherTimeoutOrderStillRejectsIdentitySubstitution() {
        for (timeoutFirst in listOf(true, false)) {
            val p = timedOut(timeoutFirst); val g = p.begin(at(33))!!
            assertFalse(p.confirm(g, binding.copy(spki = "different-test-spki"), true, at(33)))
            assertEquals(Denial.BINDING, p.state.denial); assertNull(p.begin(at(34))); hidden(p)
        }
    }
}
