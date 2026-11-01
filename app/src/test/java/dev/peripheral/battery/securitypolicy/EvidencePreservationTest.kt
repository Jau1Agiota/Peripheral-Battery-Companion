package dev.peripheral.battery.securitypolicy

import dev.peripheral.battery.contract.BatterySnapshotV1
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant

@RunWith(Parameterized::class)
class EvidencePreservationTest(private val file: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "E3 evidence: {0}")
        fun cases() = listOf("absent-with-lastknown", "approximate-both", "legacy-g915-observation",
            "null-receipt-times", "null-unavailable-all", "precise-both-hyperx-estimate", "sequence-first",
            "sequence-repeat", "sequence-restarted", "sequence-second", "stale-lastknown-all",
            "unknown-confidence-both", "unsupported-both", "zero-all").map { arrayOf(it) }
    }
    @Test fun exactE2PublicationRetainsEveryWireFieldAcrossDuplicateAndLoss() {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/interop/e2/$file.json")).use { it.readBytes() }
        val expected = BatterySnapshotV1.parse(bytes)
        val epoch = Instant.parse("2026-10-31T00:00:00Z")
        fun sample(n: Long) = Sample(epoch.plusSeconds(n), n)
        val binding = Binding("test-endpoint", "test-leaf", "test-pin", "test-enrollment", "test-phone", "test-nonce", "test-verifier")
        val p = SessionPolicy(Authorization(1, epoch, epoch.plusSeconds(100), sample(0)), binding)
        val g = p.begin(sample(0))!!
        assertTrue(p.confirm(g, binding, true, sample(0)))
        assertTrue(p.request(g, "test-first", sample(1)))
        assertEquals(Outcome.ADVANCED, p.receive(g, "test-first", 1, expected.serverInstanceId, bytes, Commit.COMMITTED, sample(1)))
        val actual = p.state.displayed!!
        assertEquals(expected.schemaVersion, actual.schemaVersion)
        assertEquals(expected.synthetic, actual.synthetic)
        assertEquals(expected.serverInstanceId, actual.serverInstanceId)
        assertEquals(expected.revision, actual.revision)
        assertEquals(expected.capturedAt, actual.capturedAt)
        // Immutable nested data-class equality includes ALL current/lastKnown fields:
        // source/support/ownership, precision, availability/reason/freshness,
        // connection, percentages (zero/null), readAt and observedAt.
        assertEquals(expected.devices, actual.devices)
        assertThrows(UnsupportedOperationException::class.java) {
            (actual.devices as MutableList).clear()
        }
        assertTrue(p.request(g, "test-duplicate", sample(10)))
        assertEquals(Outcome.DUPLICATE, p.receive(g, "test-duplicate", 1, expected.serverInstanceId, bytes, Commit.COMMITTED, sample(10)))
        assertSame(actual, p.state.displayed)
        assertEquals(1L, p.state.progressedAt); assertEquals(10L, p.state.channelAt)
        p.disconnect()
        assertNull(p.state.displayed); assertSame(actual, p.state.lastAccepted)
    }
}
