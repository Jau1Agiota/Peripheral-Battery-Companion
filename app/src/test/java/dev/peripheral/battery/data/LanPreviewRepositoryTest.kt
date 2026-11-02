package dev.peripheral.battery.data

import dev.peripheral.battery.domain.*
import dev.peripheral.battery.ui.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LanPreviewRepositoryTest {
    private val settings = LanPreviewSettings("192.168.2.1")
    private fun fixture(name: String) = javaClass.classLoader!!.getResourceAsStream("interop/e2/$name.json")!!.use { it.readBytes() }
    private fun runSteps(repo: LanPreviewRepository) = runBlocking {
        try { repo.monitor(settings) } catch (_: CancellationException) { }
    }
    @Test fun validatesOnlyPrivateCanonicalAddressAndPort() {
        for (host in listOf("0.0.0.0", "127.0.0.1", "8.8.8.8", "192.168.002.1", "192.168.2.1/path", "localhost", "::1"))
            assertThrows(IllegalArgumentException::class.java) { LanPreviewSettings(host) }
        for (port in listOf(0, 80, 65536)) assertThrows(IllegalArgumentException::class.java) { LanPreviewSettings(settings.host, port) }
        assertEquals("http://192.168.2.1:8765/v1/battery-snapshot", settings.url)
    }
    @Test fun zeroAndPrecisionSurviveAndCancellationStopsPolling() {
        var calls = 0
        val repo = LanPreviewRepository(LanPreviewFetch { calls++; fixture("zero-all") }, { throw CancellationException() })
        runSteps(repo)
        assertEquals(1, calls)
        assertTrue(repo.snapshot().devices.all { it.batteryPercent == 0 })
        assertEquals(Precision.ESTIMATE, repo.snapshot().devices.last().precision)
        assertFalse(repo.snapshot().synthetic)
        assertEquals(ConnectionState.STALE, repo.snapshot().connection.state)
        assertEquals(LAN_PREVIEW_LABEL, repo.snapshot().connection.present().banner)
    }
    @Test fun malformedDataBacksOffAndNeverBecomesDemo() {
        val waits = mutableListOf<Long>()
        val repo = LanPreviewRepository(LanPreviewFetch { "{}".toByteArray() }, {
            waits.add(it); if (waits.size == 3) throw CancellationException()
        })
        runSteps(repo)
        assertEquals(listOf(2000L, 5000L, 15000L), waits)
        assertFalse(repo.snapshot().synthetic)
        assertTrue(repo.snapshot().devices.isEmpty())
    }
    @Test fun disconnectRetainsStaleValuesThenReconnectsWithoutDemo() {
        var calls = 0; val observed = mutableListOf<ConnectionState>(); lateinit var repo: LanPreviewRepository
        repo = LanPreviewRepository(LanPreviewFetch {
            calls++; if (calls == 2) throw java.io.IOException("controlled disconnect")
            fixture("precise-both-hyperx-estimate")
        }, {
            observed.add(repo.snapshot().connection.state)
            if (calls == 3) throw CancellationException()
        })
        runSteps(repo)
        assertEquals(listOf(ConnectionState.CONNECTED, ConnectionState.STALE, ConnectionState.CONNECTED), observed)
        assertFalse(repo.snapshot().synthetic)
        assertTrue(repo.snapshot().devices.any { it.batteryPercent != null })
    }
    @Test fun repeatedPublicationEventuallyBecomesStale() {
        var clock = 0L; var loops = 0; val states = mutableListOf<ConnectionState>(); lateinit var repo: LanPreviewRepository
        repo = LanPreviewRepository(LanPreviewFetch { fixture("zero-all") }, {
            states.add(repo.snapshot().connection.state); clock += 31_000_000_000L
            if (++loops == 2) throw CancellationException()
        }, { clock })
        runSteps(repo)
        assertEquals(listOf(ConnectionState.CONNECTED, ConnectionState.STALE), states)
    }
    @Test fun unknownAndLastKnownAreNotPromotedToCurrent() {
        val repo = LanPreviewRepository(LanPreviewFetch { fixture("null-unavailable-all") }, { throw CancellationException() })
        runSteps(repo)
        assertTrue(repo.snapshot().devices.all { it.batteryPercent == null })
    }
    @Test fun clientRejectsUnapprovedDestinationBeforeNetworking() { runBlocking {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { LanPreviewClient("192.168.2.2").fetch(settings) } }
    } }

    @Test fun concurrentMonitorCannotDispatchASecondFetch() = runBlocking {
        var calls = 0
        val entered = CompletableDeferred<Unit>()
        val repo = LanPreviewRepository(LanPreviewFetch { calls++; entered.complete(Unit); awaitCancellation() })
        val first = launch { repo.monitor(settings) }
        entered.await()
        try {
            assertThrows(IllegalStateException::class.java) { runBlocking { repo.monitor(settings) } }
            assertEquals(1, calls)
        } finally { first.cancelAndJoin() }
    }
}
