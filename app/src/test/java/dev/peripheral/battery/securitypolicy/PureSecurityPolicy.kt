package dev.peripheral.battery.securitypolicy

import dev.peripheral.battery.contract.BatterySnapshotV1
import java.time.Duration
import java.time.Instant
import java.util.UUID

// TEST SOURCE ONLY. Observations/commit results are caller-supplied fiction, not
// TLS evidence, credentials, enrollment, platform storage or rollback protection.
internal enum class Commit { COMMITTED, NOT_COMMITTED, UNCERTAIN }
internal data class Sample(val utc: Instant, val seconds: Long, val certain: Boolean = true)
internal enum class Denial {
    CLOCK, EXPIRED, REVOKED, AUTHORIZATION, PERSISTENCE, HISTORY_FULL,
    RETIRED_INSTANCE, REGRESSION, MODIFIED_DUPLICATE, INPUT, BINDING, DEADLINE, STOPPED,
}

internal class Authorization(
    val generation: Long, private val issued: Instant, private val expires: Instant,
    private val start: Sample,
) {
    private val budget = Duration.between(start.utc, expires)
    private var previous = start
    private var revocationPending = false
    private var revocationFinalized = false
    var denial: Denial? = null
        private set
    init {
        require(generation > 0 && issued < expires && issued <= start.utc)
        require(start.seconds >= 0 && start.certain && start.utc < expires)
    }
    fun check(now: Sample): Boolean {
        if (denial != null) return false
        if (!now.certain || now.seconds < previous.seconds || now.utc < previous.utc) {
            denial = Denial.CLOCK; return false
        }
        previous = now
        if (now.utc >= expires || Duration.ofSeconds(now.seconds - start.seconds) >= budget) {
            denial = Denial.EXPIRED; return false
        }
        return true
    }
    fun requestRevocation() {
        if (revocationFinalized) return
        revocationPending = true
        denial = Denial.REVOKED
    }
    // Separate event: requests were already denied while the commit was pending.
    fun finishRevocation(commit: Commit): Boolean {
        if (!revocationPending || revocationFinalized) return false
        revocationPending = false; revocationFinalized = true
        denial = if (commit == Commit.COMMITTED) Denial.REVOKED else Denial.PERSISTENCE
        return commit == Commit.COMMITTED
    }
    fun revoke(commit: Commit): Boolean { requestRevocation(); return finishRevocation(commit) }
}

internal enum class Outcome { ADVANCED, DUPLICATE, DENIED }
private class ReplayLedger {
    private class Water(val revision: Long, val bytes: ByteArray)
    private val history = mutableMapOf<UUID, Water>()
    private var current: UUID? = null
    val size get() = history.size
    var denial: Denial? = null
        private set
    fun missingOrRolledBackState() { denial = Denial.PERSISTENCE }
    fun reject(reason: Denial): Outcome { denial = denial ?: reason; return Outcome.DENIED }

    // One ledger is scoped externally to ONE fixed PC trust/authorization generation.
    // No recovery/reset/import API: constructing a replacement is not recovery.
    fun observe(snapshot: BatterySnapshotV1, bytes: ByteArray, commit: Commit): Outcome {
        if (denial != null) return Outcome.DENIED
        val id = snapshot.serverInstanceId
        if (snapshot.synthetic || snapshot.revision <= 0) return reject(Denial.INPUT)
        if (current != id && history.containsKey(id)) return reject(Denial.RETIRED_INSTANCE)
        if (current != id && history.size >= 64) return reject(Denial.HISTORY_FULL)
        // Includes uncertainty about already committed history on duplicate reads.
        if (commit != Commit.COMMITTED) return reject(Denial.PERSISTENCE)
        val old = history[id]
        if (old != null) {
            if (snapshot.revision < old.revision) return reject(Denial.REGRESSION)
            if (snapshot.revision == old.revision) return if (old.bytes.contentEquals(bytes)) Outcome.DUPLICATE
                else reject(Denial.MODIFIED_DUPLICATE)
        }
        history[id] = Water(snapshot.revision, bytes.copyOf())
        current = id
        return Outcome.ADVANCED
    }
}

/** Synthetic labels only. This comparison cannot validate a certificate or TLS. */
internal data class Binding(
    val endpoint: String, val leaf: String, val spki: String, val enrollment: String,
    val phone: String, val phoneNonce: String, val verifier: String, val protocol: Int = 1,
)
internal fun matchesApprovedContext(approved: Binding, observed: Binding, peerVerified: Boolean) =
    peerVerified && approved.protocol == 1 && approved == observed

internal enum class Phase { DISCONNECTED, CONNECTING, AWAITING_RESPONSE, CONNECTED, ERROR, STOPPED }
internal data class PolicyState(
    val phase: Phase = Phase.DISCONNECTED,
    val lastAccepted: BatterySnapshotV1? = null,
    val displayed: BatterySnapshotV1? = null,
    val channelAt: Long? = null,
    val progressedAt: Long? = null,
    val denial: Denial? = null,
)

/** No clocks, timers, random generation, sockets, crypto, callbacks or file I/O. */
internal class SessionPolicy(
    private val authorization: Authorization,
    private val approved: Binding,
) {
    private val ledger = ReplayLedger()
    private var generation = 0L
    private var pending: String? = null
    private val used = mutableSetOf<String>()
    private var previous: Sample? = null
    private var begunAt = 0L
    val historySize get() = ledger.size
    var state = PolicyState()
        private set

    private fun fail(reason: Denial): Boolean {
        if (state.phase == Phase.STOPPED) return false
        ledger.reject(reason)
        pending = null
        state = state.copy(phase = Phase.ERROR, displayed = null, channelAt = null, denial = reason)
        return false
    }
    private fun eligible(now: Sample): Boolean {
        if (state.phase == Phase.STOPPED || state.phase == Phase.ERROR) return false
        if (!authorization.check(now)) return fail(requireNotNull(authorization.denial))
        val old = previous
        if (old != null && (now.seconds < old.seconds || now.utc < old.utc)) return fail(Denial.CLOCK)
        previous = now
        return true
    }
    fun begin(now: Sample): Long? {
        if (!eligible(now)) return null
        if (generation == Long.MAX_VALUE) { fail(Denial.CLOCK); return null }
        generation++
        pending = null; begunAt = now.seconds
        state = state.copy(phase = Phase.CONNECTING, displayed = null, channelAt = null)
        return generation
    }
    fun confirm(g: Long, observed: Binding, verified: Boolean, now: Sample): Boolean {
        if (g != generation || state.phase != Phase.CONNECTING) return false
        if (!checkDeadline(now)) return false
        if (!matchesApprovedContext(approved, observed, verified)) return fail(Denial.BINDING)
        state = state.copy(phase = Phase.AWAITING_RESPONSE)
        return true
    }
    // Models preparation; must be checked again at the eventual release boundary.
    fun request(g: Long, nonce: String, now: Sample): Boolean {
        if (g != generation || state.phase !in setOf(Phase.AWAITING_RESPONSE, Phase.CONNECTED)) return false
        if (!checkDeadline(now)) return false
        if (pending != null || nonce.isBlank() || !used.add(nonce)) return fail(Denial.BINDING)
        pending = nonce
        return true
    }
    fun mayRelease(g: Long, now: Sample): Boolean =
        g == generation && pending != null && checkDeadline(now)

    fun receive(g: Long, nonce: String, authGeneration: Long, publisher: UUID,
                bytes: ByteArray, commit: Commit, now: Sample): Outcome {
        if (g != generation || state.phase !in setOf(Phase.AWAITING_RESPONSE, Phase.CONNECTED)) return Outcome.DENIED
        // Same transient transition regardless of whether timer or response wins.
        // checkDeadline checks authorization/clock failures before timeout handling.
        if (!checkDeadline(now)) return Outcome.DENIED
        if (pending == null || pending != nonce || authGeneration != authorization.generation) {
            fail(Denial.BINDING); return Outcome.DENIED
        }
        pending = null
        val parsed = try { BatterySnapshotV1.parse(bytes) } catch (_: IllegalArgumentException) {
            fail(Denial.INPUT); return Outcome.DENIED
        }
        if (parsed.serverInstanceId != publisher) { fail(Denial.BINDING); return Outcome.DENIED }
        val result = ledger.observe(parsed, bytes, commit)
        if (result == Outcome.DENIED) { fail(requireNotNull(ledger.denial)); return result }
        val accepted = if (result == Outcome.ADVANCED) parsed else state.lastAccepted
        state = state.copy(phase = Phase.CONNECTED, lastAccepted = accepted, displayed = accepted,
            channelAt = now.seconds, progressedAt = if (result == Outcome.ADVANCED) now.seconds else state.progressedAt)
        return result
    }
    fun checkDeadline(now: Sample): Boolean {
        if (!eligible(now)) return false
        if (state.phase in setOf(Phase.CONNECTING, Phase.AWAITING_RESPONSE, Phase.CONNECTED) &&
            now.seconds - (state.channelAt ?: begunAt) >= 30) {
            pending = null
            state = state.copy(phase = Phase.DISCONNECTED, displayed = null, channelAt = null)
            return false
        }
        return true
    }
    fun requestRevocation() {
        authorization.requestRevocation()
        fail(requireNotNull(authorization.denial))
    }
    fun finishRevocation(commit: Commit): Boolean {
        val acknowledged = authorization.finishRevocation(commit)
        authorization.denial?.let { fail(it) }
        return acknowledged
    }
    fun revoke(commit: Commit): Boolean { requestRevocation(); return finishRevocation(commit) }
    fun uncertainState() { ledger.missingOrRolledBackState(); fail(Denial.PERSISTENCE) }
    fun disconnect() {
        if (state.phase in setOf(Phase.ERROR, Phase.STOPPED)) return
        pending = null
        state = state.copy(phase = Phase.DISCONNECTED, displayed = null, channelAt = null)
    }
    fun stop() {
        pending = null
        state = state.copy(phase = Phase.STOPPED, displayed = null, channelAt = null)
    }
}
