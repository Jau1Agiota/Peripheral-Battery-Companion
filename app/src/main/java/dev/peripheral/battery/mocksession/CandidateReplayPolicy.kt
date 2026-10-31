package dev.peripheral.battery.mocksession

import java.util.UUID

/** In-memory CANDIDATE policy only: no durable rollback protection, crypto, or A2.3B integration. */
internal class CandidateReplayPolicy(private val capacity: Int) {
    private data class Scope(val identity: MockPeerIdentity, val publisher: UUID)
    private data class HighWater(val revision: Long, val bytes: ByteArray)
    private val current = mutableMapOf<MockPeerIdentity, UUID>()
    private val history = mutableMapOf<Scope, HighWater?>()
    init { require(capacity in 1..64) }

    fun bind(identity: MockPeerIdentity, publisher: UUID): MockDenial? {
        val scope = Scope(identity, publisher)
        if (current[identity] == publisher) return null
        if (history.containsKey(scope)) return MockDenial.RETIRED_INSTANCE
        if (history.size >= capacity) return MockDenial.HISTORY_FULL
        history[scope] = null
        current[identity] = publisher
        return null
    }

    fun observe(identity: MockPeerIdentity, publisher: UUID, revision: Long, bytes: ByteArray): Pair<MockOutcome, MockDenial?> {
        val scope = Scope(identity, publisher)
        if (current[identity] != publisher || !history.containsKey(scope)) return MockOutcome.DENIED to MockDenial.UNBOUND_INSTANCE
        val old = history[scope]
        if (old != null) {
            if (revision < old.revision) return MockOutcome.DENIED to MockDenial.REPLAY_REGRESSION
            if (revision == old.revision) return if (old.bytes.contentEquals(bytes)) MockOutcome.DUPLICATE to null
                else MockOutcome.DENIED to MockDenial.MODIFIED_DUPLICATE
        }
        history[scope] = HighWater(revision, bytes.copyOf())
        return MockOutcome.ACCEPTED to null
    }
}
