package dev.peripheral.battery

import dev.peripheral.battery.foundations.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Synthetic metadata/fake commit tests only. No native crypto, sockets, TLS, OS credential stores. */
class D33ATransportTest {
    private val start=EpochSample("boot-a",ClockSample(10,10,true))
    private val limits=TransportPolicyData(2,20,10,10,5,5,10,5,true)
    private fun bytes(name: String) = javaClass.classLoader!!.getResourceAsStream("d3-3a/$name")!!.use { it.readBytes() }
    private fun fixture(name: String) = JSONArray(bytes("$name.json").toString(Charsets.UTF_8))
    private fun kind(s: String)=if(s=="IP") SanKind.IP else SanKind.DNS
    private fun rejects(block: ()->Unit) { try { block() } catch(_: Exception) { return }; fail("Expected rejection") }
    @Test fun identityVectorsProduceEquivalentSyntheticDecisions() {
        val f=fixture("identity")
        for(i in 0 until f.length()) {
            val c=f.getJSONObject(i); val e=c.getJSONObject("expected"); val t=c.getJSONObject("seen")
            val expected=EndpointExpectation(e.getString("host"),e.getInt("port"),kind(e.getString("kind")),e.getString("anchor"),e.getString("pin"))
            val seen=SyntheticTrust(t.getString("host"),t.getInt("port"),kind(t.getString("kind")),t.getString("san"),
                t.getString("anchor"),t.getString("pin"),t.getLong("notBefore"),t.getLong("notAfter"),t.getBoolean("path"),t.getBoolean("eku"))
            val session=SyntheticTlsSession(expected,start); val chain=t.getJSONArray("chain")
            val chains=(0 until chain.length()).map { chain.getString(it) }
            val times=c.getJSONArray("times")
            for(j in 0 until times.length()) {
                val n=times.getJSONObject(j)
                val now=EpochSample(n.getString("boot"),ClockSample(n.getLong("utc"),n.getLong("tick"),n.getBoolean("certain")))
                assertEquals(c.getString("name"),c.getJSONArray("accept").getBoolean(j),
                    session.evaluate(seen,now,c.getJSONArray("revoked").getBoolean(j),chains))
            }
        }
    }
    private class Store : AtomicSyntheticLedger {
        var mode=0; var writes=0; var version=0L
        var duringWrite: (()->Unit)?=null
        var persisted: SyntheticCredentialEntry?=null
        override fun commitAndFlush(expectedVersion: Long,replacement: SyntheticCredentialEntry): CommitAcknowledgment {
            writes++; duringWrite?.invoke()
            if(expectedVersion!=version || mode==1) return CommitAcknowledgment(CommitDisposition.NOT_COMMITTED,version)
            if(mode==6) { persisted=null; return CommitAcknowledgment(CommitDisposition.UNCERTAIN,version) }
            persisted=replacement; version++
            if(mode==2) error("synthetic lost acknowledgment")
            return CommitAcknowledgment(if(mode==3) CommitDisposition.UNCERTAIN else CommitDisposition.COMMITTED_AND_FLUSHED,
                if(mode==4) expectedVersion else if(mode==5) version+1 else version)
        }
    }
    @Test fun credentialTracesCoverCommitCrashRotationRevocationAndLockedRestore() {
        val f=fixture("credentials")
        for(i in 0 until f.length()) {
            val trace=f.getJSONObject(i); val store=Store()
            var ledger=SyntheticCredentialLedger(store,start,8,true)
            val steps=trace.getJSONArray("steps")
            for(j in 0 until steps.length()) {
                val s=steps.getJSONObject(j); store.mode=s.getInt("mode")
                val result=when(s.getString("op")) {
                    "issue" -> ledger.issueSynthetic("phone","old",20,20,start)
                    "rotate","replay-rotate" -> ledger.rotateSynthetic("phone","new",20,20,start)
                    "read","old-read" -> ledger.readSyntheticDecision("phone","old","snapshot.read",start)
                    "read-new" -> ledger.readSyntheticDecision("phone","new","snapshot.read",start)
                    "wrong-scope" -> ledger.readSyntheticDecision("phone","old","admin",start)
                    "expired-read" -> ledger.readSyntheticDecision("phone","old","snapshot.read",EpochSample("boot-a",ClockSample(20,11,true)))
                    "rollback-read" -> ledger.readSyntheticDecision("phone","old","snapshot.read",EpochSample("boot-a",ClockSample(9,11,true)))
                    "reboot-read" -> ledger.readSyntheticDecision("phone","old","snapshot.read",EpochSample("boot-b",ClockSample(10,10,true)))
                    "revoke" -> ledger.revokeSynthetic("phone",start)
                    "crash" -> { ledger.crashOrLostAcknowledgment(); false }
                    "restore" -> { ledger=SyntheticCredentialLedger(store,start,8,false); false }
                    else -> error("Unknown synthetic action")
                }
                assertEquals(trace.getString("name"),s.getBoolean("accept"),result)
                assertEquals(trace.getString("name"),s.getInt("state"),ledger.state.ordinal)
            }
        }
    }
    @Test fun freshnessParityIncludesLongMaximumRevision() {
        val f=fixture("freshness"); val p=PublicationTracker(8)
        for(i in 0 until f.length()) {
            val s=f.getJSONObject(i)
            assertEquals(s.getInt("result"),p.observe(s.getString("session"),s.getLong("revision"),
                s.getBoolean("authenticated"),s.getBoolean("synthetic"),s.getBoolean("uncached")).ordinal)
        }
    }
    @Test fun additionalPayloadRejectionsAndDigestParity() {
        bytes("SHA256SUMS").toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.forEach {
            val (digest,path)=it.split("  "); val payload=bytes(path)
            assertEquals(digest,MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { b->"%02x".format(b) })
            if(path.startsWith("rejected/")) rejects { SnapshotWirePolicy.read(payload) }
        }
        rejects { SnapshotWirePolicy.read(ByteArray(65537)) }
    }
    @Test fun nativeEvidenceHasNoIssuerAndDisabledBoundaryNeverCallsSnapshotSource() {
        assertTrue(AuthenticatedTlsPeer::class.java.constructors.isEmpty())
        val b=DisabledTlsBoundary()
        assertEquals(TransportDecision.NATIVE_PROOF_REQUIRED,b.validate(null))
        assertEquals(TransportDecision.DENIED,b.authorizeSnapshotRead(null))
        val source=object : ReadOnlySnapshotSource {
            override fun readAlreadyPublishedSnapshot(): ByteArray = error("Must not read production")
        }
        assertNull(b.retrieveSnapshot(null,source))
    }
    @Test fun lifecycleRemainsInertAndRestartOff() {
        val l=InertTransportLifecycle(); assertEquals(TransportState.OFF,l.state)
        assertFalse(l.evaluateSynthetic(true)); assertTrue(l.configureSynthetic(true,limits))
        assertEquals(TransportState.CONFIGURED,l.state); assertTrue(l.evaluateSynthetic(true))
        assertEquals(TransportState.ELIGIBLE,l.state); assertFalse(l.evaluateSynthetic(false))
        assertFalse(l.evaluateSynthetic(true)); l.restart(); assertEquals(TransportState.OFF,l.state)
        assertFalse(l.configureSynthetic(false,limits)); l.restart()
        assertTrue(l.configureSynthetic(true,limits)); l.shutdown(); l.restart()
        assertFalse(l.configureSynthetic(true,limits)); assertEquals(TransportState.SHUTDOWN,l.state)
    }
    @Test fun limitsRejectOverflowTimeoutAndMissingCancellation() {
        assertFalse(limits.copy(headerBytes=Int.MAX_VALUE,bodyBytes=Int.MAX_VALUE,requestBytes=Int.MAX_VALUE).valid)
        assertFalse(limits.copy(handshakeTicks=0).valid); assertFalse(limits.copy(headerTicks=-1).valid)
        assertFalse(limits.copy(requestTicks=0).valid); assertFalse(limits.copy(shutdownTicks=0).valid)
        assertFalse(limits.copy(cancelOnStop=false).valid); assertTrue(limits.valid)
    }
    @Test fun reentrantCrashAndReadDuringCommitCannotResurrect() {
        val store=Store(); val ledger=SyntheticCredentialLedger(store,start,8,true)
        store.duringWrite={
            assertFalse(ledger.readSyntheticDecision("phone","old","snapshot.read",start))
            ledger.crashOrLostAcknowledgment()
        }
        assertFalse(ledger.issueSynthetic("phone","old",20,20,start)); assertEquals(LedgerState.CLOSED,ledger.state)
        assertFalse(ledger.readSyntheticDecision("phone","old","snapshot.read",start))
        assertFalse(SyntheticCredentialLedger(store,start,8,false).readSyntheticDecision("phone","old","snapshot.read",start))
    }
    @Test fun concurrentRevokeReadAndRepeatedRevokeCannotRestore() {
        val store=Store(); val ledger=SyntheticCredentialLedger(store,start,8,true)
        assertTrue(ledger.issueSynthetic("phone","old",20,20,start))
        val pool=Executors.newFixedThreadPool(8)
        try {
            pool.invokeAll((0..31).map { i->Callable {
                if(i%2==0) ledger.revokeSynthetic("phone",start) else ledger.readSyntheticDecision("phone","old","snapshot.read",start)
            } }).forEach { it.get() }
        } finally { pool.shutdownNow() }
        assertFalse(ledger.readSyntheticDecision("phone","old","snapshot.read",start))
        assertTrue(ledger.revokeSynthetic("phone",start)); assertEquals(2,store.writes)
    }
    @Test fun invalidRestoreAndBootClockNeverBecomeReady() {
        val store=Store()
        listOf(EpochSample("",ClockSample(10,10,true)),EpochSample("boot-a",ClockSample(10,-1,true)),
            EpochSample("boot-a",ClockSample(10,10,false))).forEach {
            assertFalse(SyntheticCredentialLedger(store,it,8,true).issueSynthetic("phone","old",20,20,start))
        }
        assertFalse(SyntheticCredentialLedger(store,start,0,true).issueSynthetic("phone","old",20,20,start))
        assertEquals(0,store.writes)
    }
}
