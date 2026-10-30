package dev.peripheral.battery

import dev.peripheral.battery.foundations.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Pure synthetic policy tests. No TLS, Keystore, socket, credential or UI integration. */
class D3FoundationTest {
    private fun now(utc: Long = 10, tick: Long = 10, certain: Boolean = true) = ClockSample(utc,tick,certain)
    private val pin = "a".repeat(64)
    private val anchor = "b".repeat(64)
    private val endpoint = EndpointExpectation("192.168.1.2",41000,SanKind.IP,anchor,pin)
    private val trust = SyntheticTrust("192.168.1.2",41000,SanKind.IP,"192.168.1.2",anchor,pin,0,100,true,true)
    private fun bytes(path: String) = javaClass.classLoader!!.getResourceAsStream(path)!!.use { it.readBytes() }
    private fun rejects(block: () -> Unit) { try { block() } catch (_: Exception) { return }; fail("Expected rejection") }
    private class Store(var fail: Boolean = false, var throwAfterEffect: Boolean = false) : EnrollmentMetadataStore, RevocationMetadataStore {
        var writes = 0
        var duringWrite: (() -> Unit)? = null
        override fun commitSyntheticEnrollment(flow: String): Boolean { writes++; duringWrite?.invoke(); if(throwAfterEffect) error("synthetic side effect"); return !fail }
        override fun commitSyntheticRevocation(): Boolean { writes++; duringWrite?.invoke(); if(throwAfterEffect) error("synthetic side effect"); return !fail }
    }
    @Test fun consentIsOffAfterRestartAndInvalidEvidenceLatches() {
        val c = ConsentGate(); assertFalse(c.evaluate(true))
        c.localDecision(false,true); assertFalse(c.evaluate(true))
        c.localDecision(true,false); assertFalse(c.evaluate(true))
        c.localDecision(true,true); assertTrue(c.evaluate(true))
        assertFalse(c.evaluate(false)); assertFalse(c.evaluate(true))
        c.localDecision(true,true); c.restart(); assertFalse(c.evaluate(true))
        c.shutdown(); c.localDecision(true,true); c.restart(); assertFalse(c.evaluate(true))
    }
    @Test fun admissionEnforcesLimitsAndSingleLeaseRelease() {
        val b = AdmissionBudget(AdmissionLimits(1,10,2,5))
        assertNull(b.enter(false,1,0,0)); assertNull(b.enter(true,11,0,0))
        assertNull(b.enter(true,1,3,0)); assertNull(b.enter(true,1,0,5)); assertNull(b.enter(true,1,0,-1))
        val lease = b.enter(true,10,2,4)!!; assertNull(b.enter(true,1,0,0))
        assertTrue(b.leave(lease)); assertFalse(b.leave(lease)); assertNotNull(b.enter(true,1,0,0))
        assertNull(AdmissionBudget(AdmissionLimits(0,10,2,5)).enter(true,1,0,0))
    }
    @Test fun admissionRaceHasOneWinner() {
        val b = AdmissionBudget(AdmissionLimits(1,10,2,5))
        val pool = Executors.newFixedThreadPool(8)
        try { assertEquals(1, pool.invokeAll((1..32).map { Callable { b.enter(true,1,0,0) } }).count { it.get() != null }) }
        finally { pool.shutdownNow() }
    }
    @Test fun syntheticTrustRequiresAllIndependentChecks() {
        assertTrue(TrustMetadataPolicy.decide(endpoint,trust,now()))
        val wrong = listOf(trust.copy(pin=anchor),trust.copy(anchor=pin),trust.copy(san="192.168.1.3"),
            trust.copy(kind=SanKind.DNS),trust.copy(pathValid=false),trust.copy(serverEku=false),
            trust.copy(notAfter=10),trust.copy(notBefore=11),trust.copy(port=41001),trust.copy(host="rogue"))
        wrong.forEach { assertFalse(TrustMetadataPolicy.decide(endpoint,it,now())) }
        assertFalse(TrustMetadataPolicy.decide(endpoint,trust,now(certain=false)))
        listOf("0.0.0.0","127.0.0.1","8.8.8.8","host.local","192.168.001.2","fe80::1","::ffff:192.168.1.2").forEach {
            assertFalse(TrustMetadataPolicy.decide(endpoint.copy(host=it),trust.copy(host=it,san=it),now()))
        }
        assertTrue(TrustMetadataPolicy.decide(endpoint.copy(host="fd00::1"),trust.copy(host="fd00::1",san="fd00::1"),now()))
    }
    @Test fun pairingDeniesConsentMismatchAndExpiryBeforeWriting() {
        val s = Store()
        assertFalse(PairingLifecycle(false,now(),20,20).claim("flow",true,now()))
        val bad = PairingLifecycle(true,now(),20,20)
        assertFalse(bad.claim("flow",false,now())); assertFalse(bad.claim("flow",true,now()))
        assertFalse(PairingLifecycle(true,now(),20,20).claim("flow",true,now(20,11)))
        val p = PairingLifecycle(true,now(),20,20); assertTrue(p.claim("flow",true,now()))
        assertFalse(p.confirm("flow",false,now(),s)); assertEquals(0,s.writes)
    }
    @Test fun singleUseClaimAndConfirmationRaceWriteOnce() {
        val p = PairingLifecycle(true,now(),20,20); val winners = AtomicInteger()
        val pool = Executors.newFixedThreadPool(8); val s = Store()
        try {
            pool.invokeAll((1..32).map { Callable { if(p.claim("flow",true,now())) winners.incrementAndGet() } }).forEach { it.get() }
            assertEquals(1,winners.get())
            pool.invokeAll((1..32).map { Callable { p.confirm("flow",true,now(),s) } }).forEach { it.get() }
            assertEquals(1,s.writes); assertEquals(PairState.CONSUMED,p.state)
            assertFalse(p.claim("other",true,now()))
        } finally { pool.shutdownNow() }
    }
    @Test fun failedWriteAndErrorAfterEffectNeverReissue() {
        listOf(false,true).forEach {
            val p = PairingLifecycle(true,now(),20,20); val s = Store(fail=true,throwAfterEffect=it)
            assertTrue(p.claim("flow",true,now())); assertFalse(p.confirm("flow",true,now(),s))
            assertEquals(PairState.UNCERTAIN,p.state); assertFalse(p.confirm("flow",true,now(),s)); assertEquals(1,s.writes)
        }
    }
    @Test fun lostResponseCrashAndClockRollbackCannotRecover() {
        val p = PairingLifecycle(true,now(),20,20); val s = Store()
        assertTrue(p.claim("flow",true,now())); assertTrue(p.confirm("flow",true,now(),s))
        p.crashOrLostResponse(); assertFalse(p.confirm("flow",true,now(),s)); assertEquals(1,s.writes)
        val rollback = PairingLifecycle(true,now(),20,20)
        assertFalse(rollback.claim("flow",true,now(9,11))); assertEquals(PairState.CLOSED,rollback.state)
        val crash = PairingLifecycle(true,now(),20,20); crash.crashOrLostResponse(); assertFalse(crash.claim("flow",true,now()))
    }
    @Test fun credentialChecksScopeExpiryRevocationAndUncertainClock() {
        fun fresh() = CredentialPolicy(0,20,20,"snapshot.read")
        var c = fresh(); assertTrue(c.allows(true,"snapshot.read",now()))
        assertFalse(c.allows(false,"snapshot.read",now())); assertFalse(c.allows(true,"admin",now()))
        assertFalse(c.allows(true,"snapshot.read",now(20,11)))
        c = fresh(); assertFalse(c.allows(true,"snapshot.read",now(certain=false))); assertFalse(c.allows(true,"snapshot.read",now()))
        c = fresh(); assertTrue(c.allows(true,"snapshot.read",now()))
        assertFalse(c.allows(true,"snapshot.read",now(9,11))); assertFalse(c.allows(true,"snapshot.read",now(11,12)))
        c = fresh(); assertFalse(c.revoke(Store(throwAfterEffect=true))); assertFalse(c.allows(true,"snapshot.read",now()))
    }
    @Test fun demoDuplicateRecedingAndOldSessionNeverBecomeFresh() {
        val p = PublicationTracker(2)
        assertEquals(PublicationDecision.REJECTED,p.observe("a",1,true,true,true))
        assertEquals(PublicationDecision.REJECTED,p.observe("a",1,false,false,true))
        assertEquals(PublicationDecision.REJECTED,p.observe("a",1,true,false,false))
        assertEquals(PublicationDecision.NEW_SESSION_UNKNOWN_FRESHNESS,p.observe("a",1,true,false,true))
        assertEquals(PublicationDecision.DUPLICATE,p.observe("a",1,true,false,true))
        assertEquals(PublicationDecision.REJECTED,p.observe("a",0,true,false,true))
        assertEquals(PublicationDecision.ADVANCED_UNKNOWN_FRESHNESS,p.observe("a",2,true,false,true))
        assertEquals(PublicationDecision.OFFLINE,p.disconnect())
        assertEquals(PublicationDecision.REJECTED,p.observe("a",1,true,false,true))
        assertEquals(PublicationDecision.NEW_SESSION_UNKNOWN_FRESHNESS,p.observe("b",0,true,false,true))
        assertEquals(PublicationDecision.REJECTED,p.observe("a",3,true,false,true))
    }
    @Test fun schemaAndEveryVectorMatchDigestsAndExpectedDecisions() {
        assertEquals("2d9ac5bf5e5263340bcd15736df7a9f6f6c201f5f4ad30b1fb7e858ec694bdc2",MessageDigest.getInstance("SHA-256").digest(bytes("battery-snapshot-v1.schema.json")).joinToString("") { b -> "%02x".format(b) })
        bytes("SHA256SUMS").toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.forEach {
            val (digest,path) = it.split("  ")
            assertEquals(path,digest,MessageDigest.getInstance("SHA-256").digest(bytes(path)).joinToString("") { b -> "%02x".format(b) })
        }
        val cases = JSONArray(bytes("scenarios.json").toString(Charsets.UTF_8))
        for(i in 0 until cases.length()) {
            val c = cases.getJSONObject(i); val payload = bytes(c.getString("file"))
            if(c.getBoolean("accept")) assertTrue(SnapshotWirePolicy.read(payload).getBoolean("synthetic"))
            else rejects { SnapshotWirePolicy.read(payload) }
        }
    }
    @Test fun zeroNullAndRetainedProvenanceRemainDistinct() {
        val zero = SnapshotWirePolicy.read(bytes("golden/zero-and-unknown.json")).getJSONArray("devices")
        assertEquals(0,zero.getJSONObject(0).getJSONObject("current").getInt("batteryPercent"))
        assertTrue(zero.getJSONObject(1).getJSONObject("current").isNull("batteryPercent"))
        val stale = SnapshotWirePolicy.read(bytes("golden/stale-last-known.json")).getJSONArray("devices").getJSONObject(0)
        assertEquals("STALE",stale.getString("freshness"))
        assertEquals(99,stale.getJSONObject("lastKnown").getInt("batteryPercent"))
        assertEquals("GHUB_GATEWAY",stale.getJSONObject("lastKnown").getJSONObject("source").getString("kind"))
    }

    @Test fun crashDuringSyntheticCommitCannotPromoteUncertainty() {
        val p = PairingLifecycle(true,now(),20,20)
        val s = Store(); s.duringWrite = { p.crashOrLostResponse() }
        assertTrue(p.claim("flow",true,now())); assertFalse(p.confirm("flow",true,now(),s))
        assertEquals(PairState.UNCERTAIN,p.state); assertEquals(1,s.writes)
    }
    @Test fun tickExpiryRollbackAndBoundedSessionHistoryFailClosed() {
        assertFalse(PairingLifecycle(true,now(),20,20).claim("flow",true,now(11,20)))
        assertFalse(PairingLifecycle(true,now(),20,20).claim("flow",true,now(11,9)))
        val p = PairingLifecycle(true,now(),20,20)
        assertTrue(p.claim("flow",true,now())); assertFalse(p.confirm("wrong",true,now(),Store()))
        assertFalse(CredentialPolicy(0,20,20,"snapshot.read").allows(true,"snapshot.read",now(11,20)))
        val t = PublicationTracker(1)
        assertEquals(PublicationDecision.NEW_SESSION_UNKNOWN_FRESHNESS,t.observe("a",0,true,false,true))
        assertEquals(PublicationDecision.NEW_SESSION_UNKNOWN_FRESHNESS,t.observe("b",0,true,false,true))
        assertEquals(PublicationDecision.REJECTED,t.observe("c",0,true,false,true))
    }
    @Test fun wireRetainsReceiptObservationAndLastKnownWithoutUiMapping() {
        val text = bytes("golden/stale-last-known.json").toString(Charsets.UTF_8)
            .replace("\"synthetic\": true","\"synthetic\": false")
            .replace("\"readAt\": null","\"readAt\": \"2026-01-02T00:00:00Z\"")
        val retained = SnapshotWirePolicy.read(text.toByteArray()).getJSONArray("devices").getJSONObject(0).getJSONObject("lastKnown")
        assertEquals("2026-01-02T00:00:00Z",retained.getString("readAt"))
        assertTrue(retained.isNull("observedAt"))
    }
    @Test fun fakeTransportRejectsOversizeMalformedDepthAndExtensions() {
        listOf(ByteArray(65537),"{}".toByteArray(),("[".repeat(18)+"]".repeat(18)).toByteArray(),
            "{unquoted:true}".toByteArray(),"{'x':1}".toByteArray(),"{\"x\":1,}".toByteArray(),
            byteArrayOf(0xc3.toByte(),0x28)).forEach { payload ->
            val transport = SyntheticSnapshotTransport { payload }
            rejects { SnapshotWirePolicy.read(transport.readSyntheticPayload()) }
        }
    }
}
