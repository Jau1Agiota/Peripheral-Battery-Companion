package dev.peripheral.battery.foundations

// Synthetic metadata decisions only. No sockets, TLS adapters, tokens, storage or UI wiring.
data class ClockSample(val utcSeconds: Long, val ticks: Long, val certain: Boolean)
class ClockGuard {
    private var previous: ClockSample? = null
    fun observe(now: ClockSample): Boolean {
        val old = previous
        if (!now.certain || now.ticks < 0 || old != null &&
            (now.utcSeconds < old.utcSeconds || now.ticks < old.ticks)) return false
        previous = now
        return true
    }
}
enum class ConsentState { OFF, APPROVED, STOPPED, SHUTDOWN }
class ConsentGate {
    var state = ConsentState.OFF; private set
    fun localDecision(consent: Boolean, valid: Boolean) {
        if (state != ConsentState.SHUTDOWN) state = if (consent && valid) ConsentState.APPROVED else ConsentState.STOPPED
    }
    fun evaluate(valid: Boolean): Boolean {
        if (!valid && state == ConsentState.APPROVED) state = ConsentState.STOPPED
        return state == ConsentState.APPROVED
    }
    fun restart() { if (state != ConsentState.SHUTDOWN) state = ConsentState.OFF }
    fun shutdown() { state = ConsentState.SHUTDOWN }
}
data class AdmissionLimits(val concurrent: Int, val headerBytes: Int, val bodyBytes: Int, val durationTicks: Long) {
    val valid get() = concurrent > 0 && headerBytes > 0 && bodyBytes >= 0 && durationTicks > 0
}
class AdmissionBudget(private val limits: AdmissionLimits) {
    private val active = mutableSetOf<Long>()
    private var sequence = 0L
    @Synchronized fun enter(consent: Boolean, headers: Int, body: Int, elapsed: Long): Long? {
        if (!consent || !limits.valid || headers < 0 || body < 0 || elapsed < 0 ||
            headers > limits.headerBytes || body > limits.bodyBytes || elapsed >= limits.durationTicks ||
            active.size >= limits.concurrent || sequence == Long.MAX_VALUE) return null
        val id = ++sequence; active.add(id); return id
    }
    @Synchronized fun leave(id: Long) = active.remove(id)
}
enum class SanKind { IP, DNS }
data class EndpointExpectation(val host: String, val port: Int, val kind: SanKind, val anchor: String, val pin: String)
data class SyntheticTrust(val host: String, val port: Int, val kind: SanKind, val san: String,
    val anchor: String, val pin: String, val notBefore: Long, val notAfter: Long, val pathValid: Boolean, val serverEku: Boolean)
object TrustMetadataPolicy {
    private fun privateLiteral(host: String): Boolean {
        // No InetAddress/getByName: even validation must never resolve DNS.
        if (host.contains(':')) {
            if (host.contains('%') || host.contains('.') || !host.matches(Regex("[0-9a-f:]+"))) return false
            if (host.windowed(2).count { it == "::" } > 1) return false
            val groups = host.split(':').filter { it.isNotEmpty() }
            if (groups.any { it.length !in 1..4 }) return false
            if (if (host.contains("::")) groups.size >= 8 else groups.size != 8) return false
            if (host.startsWith(":") && !host.startsWith("::") || host.endsWith(":") && !host.endsWith("::")) return false
            val first = if (host.startsWith("::")) 0 else groups.firstOrNull()?.toIntOrNull(16) ?: return false
            if (first and 0xfe00 != 0xfc00) return false
            val halves = host.split("::")
            val left = halves[0].split(':').filter { it.isNotEmpty() }.map { it.toInt(16) }
            val right = if (halves.size == 2) halves[1].split(':').filter { it.isNotEmpty() }.map { it.toInt(16) } else emptyList()
            val words = if (halves.size == 2) left + List(8 - left.size - right.size) { 0 } + right else left
            var bestStart = -1; var bestLength = 1; var index = 0
            while (index < 8) {
                if (words[index] != 0) { index++; continue }
                val start = index
                while (index < 8 && words[index] == 0) index++
                if (index - start > bestLength) { bestStart = start; bestLength = index - start }
            }
            val canonical = if (bestStart < 0) words.joinToString(":") { it.toString(16) } else
                words.take(bestStart).joinToString(":") { it.toString(16) } + "::" +
                    words.drop(bestStart + bestLength).joinToString(":") { it.toString(16) }
            return canonical == host
        }
        val parts = host.split('.')
        if (parts.size != 4 || parts.any { !it.matches(Regex("0|[1-9][0-9]{0,2}")) }) return false
        val bytes = parts.map { it.toInt() }; if (bytes.any { it > 255 }) return false
        return bytes[0] == 10 || bytes[0] == 172 && bytes[1] in 16..31 || bytes[0] == 192 && bytes[1] == 168
    }
    fun decide(expected: EndpointExpectation, seen: SyntheticTrust, now: ClockSample): Boolean =
        now.certain && expected.kind == SanKind.IP && expected.port in 1024..65535 &&
        privateLiteral(expected.host) && expected.anchor.matches(Regex("[0-9a-f]{64}")) &&
        expected.pin.matches(Regex("[0-9a-f]{64}")) &&
        seen.host == expected.host && seen.port == expected.port && seen.kind == expected.kind &&
        seen.san == expected.host && seen.anchor == expected.anchor && seen.pin == expected.pin &&
        seen.pathValid && seen.serverEku && seen.notBefore <= now.utcSeconds && now.utcSeconds < seen.notAfter
}
enum class PairState { CLOSED, OPEN, CLAIMED, ISSUING, CONSUMED, UNCERTAIN }
fun interface EnrollmentMetadataStore { fun commitSyntheticEnrollment(flow: String): Boolean }
class PairingLifecycle(localConsent: Boolean, start: ClockSample, private val expiresUtc: Long, private val expiresTick: Long) {
    private val clock = ClockGuard()
    private var flow: String? = null
    var state = if (localConsent && clock.observe(start) && expiresUtc > start.utcSeconds && expiresTick > start.ticks)
        PairState.OPEN else PairState.CLOSED; private set
    @Synchronized fun claim(candidate: String, syntheticSecretMatches: Boolean, now: ClockSample): Boolean {
        if (state != PairState.OPEN) return false
        if (!clock.observe(now) || now.utcSeconds >= expiresUtc || now.ticks >= expiresTick ||
            candidate.isBlank() || !syntheticSecretMatches) { state = PairState.CLOSED; return false }
        flow = candidate; state = PairState.CLAIMED; return true
    }
    @Synchronized fun confirm(candidate: String, localConfirmed: Boolean, now: ClockSample, store: EnrollmentMetadataStore): Boolean {
        if (state != PairState.CLAIMED) return false
        if (flow != candidate || !localConfirmed || !clock.observe(now) ||
            now.utcSeconds >= expiresUtc || now.ticks >= expiresTick) { state = PairState.CLOSED; return false }
        state = PairState.ISSUING
        state = try {
            val committed = store.commitSyntheticEnrollment(candidate)
            if (committed && state == PairState.ISSUING) PairState.CONSUMED else PairState.UNCERTAIN
        }
            catch (_: Exception) { PairState.UNCERTAIN }
        return state == PairState.CONSUMED
    }
    @Synchronized fun crashOrLostResponse() { state = PairState.UNCERTAIN }
}
fun interface RevocationMetadataStore { fun commitSyntheticRevocation(): Boolean }
class CredentialPolicy(private val issuedUtc: Long, private val expiresUtc: Long, private val expiresTick: Long, private val scope: String) {
    private val clock = ClockGuard()
    private var denied = false
    @Synchronized fun allows(syntheticVerifierMatches: Boolean, requestedScope: String, now: ClockSample): Boolean {
        if (!clock.observe(now)) denied = true
        return !denied && syntheticVerifierMatches && scope == "snapshot.read" && requestedScope == scope &&
            issuedUtc <= now.utcSeconds && expiresUtc > issuedUtc && now.utcSeconds < expiresUtc && now.ticks < expiresTick
    }
    @Synchronized fun revoke(store: RevocationMetadataStore): Boolean {
        denied = true
        return try { store.commitSyntheticRevocation() } catch (_: Exception) { false }
    }
}
enum class PublicationDecision { REJECTED, NEW_SESSION_UNKNOWN_FRESHNESS, ADVANCED_UNKNOWN_FRESHNESS, DUPLICATE, OFFLINE }
class PublicationTracker(private val historyLimit: Int) {
    init { require(historyLimit > 0) }
    private var session: String? = null
    private var revision = -1L
    private val retired = mutableSetOf<String>()
    @Synchronized fun observe(candidate: String, next: Long, authenticated: Boolean, synthetic: Boolean, uncached: Boolean): PublicationDecision {
        if (!authenticated || synthetic || !uncached || candidate.isBlank() || candidate.length > 128 || next < 0) return PublicationDecision.REJECTED
        if (candidate != session) {
            if (retired.contains(candidate) || retired.size >= historyLimit) return PublicationDecision.REJECTED
            session?.let { retired.add(it) }
            session = candidate; revision = next
            return PublicationDecision.NEW_SESSION_UNKNOWN_FRESHNESS
        }
        if (next < revision) return PublicationDecision.REJECTED
        if (next == revision) return PublicationDecision.DUPLICATE
        revision = next
        return PublicationDecision.ADVANCED_UNKNOWN_FRESHNESS
    }
    fun disconnect() = PublicationDecision.OFFLINE
}
fun interface SyntheticSnapshotTransport { fun readSyntheticPayload(): ByteArray }
