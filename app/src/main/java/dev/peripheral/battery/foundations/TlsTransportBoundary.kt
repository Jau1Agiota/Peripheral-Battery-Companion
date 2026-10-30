package dev.peripheral.battery.foundations

/** No issuer/factory exists. Synthetic metadata cannot construct authenticated platform evidence. */
class AuthenticatedTlsPeer private constructor()
interface IdentityMaterialSource { fun observeAuthenticatedIdentity(): AuthenticatedTlsPeer? }
interface TlsEvidenceValidator { fun validate(evidence: AuthenticatedTlsPeer?): TransportDecision }
interface EndpointPolicy { fun acceptSynthetic(endpoint: EndpointExpectation, seen: SyntheticTrust, time: ClockSample): Boolean }
interface ReadOnlySnapshotSource { fun readAlreadyPublishedSnapshot(): ByteArray }
interface AuthenticationDecision { fun authorizeSnapshotRead(peer: AuthenticatedTlsPeer?): TransportDecision }
interface TransportLifecycle { val state: TransportState; fun stop(); fun shutdown() }
enum class TransportDecision { NATIVE_PROOF_REQUIRED, DENIED }
class DisabledTlsBoundary : TlsEvidenceValidator, AuthenticationDecision {
    override fun validate(evidence: AuthenticatedTlsPeer?) = TransportDecision.NATIVE_PROOF_REQUIRED
    override fun authorizeSnapshotRead(peer: AuthenticatedTlsPeer?) = TransportDecision.DENIED
    @Suppress("UNUSED_PARAMETER") // Intentionally disabled; never calls source or accepts a synthetic peer.
    fun retrieveSnapshot(peer: AuthenticatedTlsPeer?, source: ReadOnlySnapshotSource): ByteArray? = null
}
data class TransportPolicyData(val connections: Int, val requestBytes: Int, val headerBytes: Int, val bodyBytes: Int,
    val handshakeTicks: Long, val headerTicks: Long, val requestTicks: Long, val shutdownTicks: Long, val cancelOnStop: Boolean) {
    val valid get() = connections > 0 && requestBytes > 0 && headerBytes > 0 && bodyBytes >= 0 &&
        headerBytes.toLong() + bodyBytes <= requestBytes && handshakeTicks > 0 && headerTicks > 0 &&
        requestTicks > 0 && shutdownTicks > 0 && cancelOnStop
}
enum class TransportState { OFF, CONFIGURED, ELIGIBLE, STOPPED, SHUTDOWN }
class InertTransportLifecycle : TransportLifecycle {
    override var state = TransportState.OFF; private set
    @Synchronized fun configureSynthetic(localConsent: Boolean, policy: TransportPolicyData): Boolean {
        if (state != TransportState.OFF) return false
        state = if (localConsent && policy.valid) TransportState.CONFIGURED else TransportState.STOPPED
        return state == TransportState.CONFIGURED
    }
    @Synchronized fun evaluateSynthetic(valid: Boolean): Boolean {
        if (state !in listOf(TransportState.CONFIGURED,TransportState.ELIGIBLE)) return false
        state = if(valid) TransportState.ELIGIBLE else TransportState.STOPPED
        return state == TransportState.ELIGIBLE // no bind/connect/start method
    }
    @Synchronized fun restart() { if (state != TransportState.SHUTDOWN) state = TransportState.OFF }
    @Synchronized override fun stop() { if (state != TransportState.SHUTDOWN) state = TransportState.STOPPED }
    @Synchronized override fun shutdown() { state = TransportState.SHUTDOWN }
}
data class EpochSample(val boot: String, val time: ClockSample)
class EpochClock(start: EpochSample) {
    private val boot = start.boot
    private val clock = ClockGuard()
    private var failed = boot.isBlank() || !clock.observe(start.time)
    fun observe(now: EpochSample): Boolean {
        failed = failed || now.boot != boot || !clock.observe(now.time)
        return !failed
    }
}
class SyntheticTlsSession(private val expected: EndpointExpectation, start: EpochSample) {
    private val clock = EpochClock(start)
    private var blocked = false
    @Synchronized fun evaluate(seen: SyntheticTrust, now: EpochSample, trustRevoked: Boolean, syntheticChain: List<String>): Boolean {
        val chain = syntheticChain.toList()
        val chainMetadata = chain.isNotEmpty() && chain.all { it.matches(Regex("[0-9a-f]{64}")) } && chain.last() == expected.anchor
        blocked = blocked || trustRevoked || !chainMetadata || !clock.observe(now) || !TrustMetadataPolicy.decide(expected,seen,now.time)
        return !blocked // never creates authenticated platform evidence
    }
}
