package dev.peripheral.battery.mocksession

import dev.peripheral.battery.contract.OfflineSnapshotProjection
import dev.peripheral.battery.domain.BatterySnapshot
import java.util.UUID

/** Synthetic labels only. Not certificates, pins, credentials or verified platform identity. */
data class MockPeerIdentity(val label: String, val identityGeneration: Long) {
    init { require(label.length in 1..64 && label.all { it.isLetterOrDigit() || it == '-' }); require(identityGeneration >= 0) }
}
data class MockTick(val value: Long, val certain: Boolean = true)
enum class MockPhase { DISCONNECTED, CONNECTING, AWAITING_RESPONSE, CONNECTED, STALE, ERROR, STOPPED }
enum class MockDenial {
    UNAUTHORIZED, WRONG_IDENTITY, WRONG_GENERATION, INVALID_PAYLOAD, SYNTHETIC_PAYLOAD,
    UNBOUND_INSTANCE, REPLAY_REGRESSION, MODIFIED_DUPLICATE, RETIRED_INSTANCE, HISTORY_FULL,
    CLOCK_UNCERTAIN, DEADLINE, CANCELED, TRUST_REVOKED, STOPPED,
}
enum class MockOutcome { NONE, AUTHENTICATED, INSTANCE_BOUND, ACCEPTED, DUPLICATE, DENIED, IGNORED_CALLBACK }
enum class MockRetention { HIDE, ALLOW_STALE_AFTER_LOSS }

/** Required explicit test seam; there is deliberately no default or real implementation. */
interface MockAuthenticationTestSeam {
    fun authorizes(identity: MockPeerIdentity, scope: String): Boolean
    fun confirmsUncachedInstance(identity: MockPeerIdentity, publisher: UUID): Boolean
}

/** Opaque mock evidence, issued only after the explicitly injected seam approves. Not real authentication. */
class MockSessionHandle private constructor(
    internal val identity: MockPeerIdentity,
    internal val localGeneration: Long,
) {
    companion object {
        internal fun throughTestSeam(seam: MockAuthenticationTestSeam, identity: MockPeerIdentity, generation: Long): MockSessionHandle? =
            if (seam.authorizes(identity, "snapshot.read")) MockSessionHandle(identity, generation) else null
    }
    override fun toString() = "MockSessionHandle (inert test evidence)"
}

/** One coherent publication. lastAccepted is evidence, never implicitly the current display. */
data class MockSessionState(
    val localGeneration: Long,
    val phase: MockPhase,
    val lastAccepted: OfflineSnapshotProjection?,
    val display: BatterySnapshot,
    val outcome: MockOutcome = MockOutcome.NONE,
    val denial: MockDenial? = null,
    val expectedIdentity: MockPeerIdentity? = null,
    val lastAcceptedIdentity: MockPeerIdentity? = null,
) {
    val mockOnly: Boolean get() = true
}
