package dev.peripheral.battery.contract

import dev.peripheral.battery.data.DemoBatteryRepository
import dev.peripheral.battery.domain.*
import dev.peripheral.battery.ui.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SnapshotContractTest {
    private val demo get() = fixture("golden/demo.json").toString(Charsets.UTF_8)
    private fun parse(s: String) = BatterySnapshotV1.parse(s.toByteArray(Charsets.UTF_8))
    private fun reject(s: String) { assertThrows(IllegalArgumentException::class.java) { parse(s) } }
    private fun change(old: String, replacement: String, text: String = demo): String {
        require(text.contains(old)); return text.replaceFirst(old, replacement)
    }
    @Test fun completeCanonicalManifestHashesMatch() {
        fixture("SHA256SUMS").toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val (expected, name) = line.split("  ", limit = 2)
            val actual = java.security.MessageDigest.getInstance("SHA-256").digest(fixture(name)).joinToString("") { "%02x".format(it) }
            assertEquals(name, expected, actual)
        }
    }
    @Test fun numericTypesAreStrictAndBounded() {
        listOf("\"1\"", "true", "null", "1.0", "1e0", "01", "+1", "9223372036854775808", "-1").forEach { reject(change("\"revision\": 0", "\"revision\": $it")) }
        listOf("true", "99.0", "9.9e1", "-1", "101", "9223372036854775808").forEach { reject(change("\"batteryPercent\": 99", "\"batteryPercent\": $it")) }
        assertEquals(Long.MAX_VALUE, parse(change("\"revision\": 0", "\"revision\": 9223372036854775807")).revision)
    }
    @Test fun uuidMustBeFullAndNonempty() {
        listOf("", "no-uuid", "1-1-1-1-1", "00000000-0000-0000-0000-000000000000").forEach {
            reject(change("00000000-0000-4000-8000-000000000001", it))
        }
    }
    @Test fun canonicalOrderIsRequired() {
        reject(demo.replace("g915", "TEMP").replace("g305", "g915").replace("TEMP", "g305"))
    }
    @Test fun duplicateAndUnknownKeysRejectedAtEveryObjectLevel() {
        listOf("schemaVersion", "id", "batteryPercent", "kind").forEach { key ->
            val match = Regex("\"$key\": ([^,\\n]+)").find(demo)!!
            reject(change(match.value, "${match.value}, ${match.value}"))
            reject(change(match.value, "${match.value}, \"extra\": null"))
        }
        reject(change("\"kind\":", "\"k\\u0069nd\": \"UNKNOWN\", \"kind\":"))
    }
    @Test fun requiredFieldsCannotBeNullOrMissing() {
        listOf("\"schemaVersion\": 1", "\"synthetic\": true", "\"precision\": \"PRECISE\"", "\"kind\": \"GHUB_GATEWAY\"").forEach {
            reject(change(it, it.substringBefore(':') + ": null")); reject(change("$it,", ""))
        }
        reject(change("\"capturedAt\": null,", ""))
        reject(change("\"readAt\": null,", ""))
    }
    @Test fun malformedInputAndTrailingContentRejected() {
        listOf("", "[]", "null", "$demo null", "$demo{}", demo.dropLast(2), demo.replaceFirst("{", "{,"), change("\"PRECISE\"", "\"bad\\x\""), change("\"PRECISE\"", "\"bad\nstring\"")).forEach(::reject)
        reject(change("\"PRECISE\"", "\"\\uD800\""))
        reject(change("\"PRECISE\"", "\"\\uDC00\""))
        assertThrows(IllegalArgumentException::class.java) { BatterySnapshotV1.parse(byteArrayOf(0xc3.toByte(), 0x28)) }
    }
    @Test fun byteLimitAndDepthAreEnforced() {
        val bytes = demo.toByteArray()
        assertEquals(3, BatterySnapshotV1.parse(bytes + ByteArray(65536 - bytes.size) { 32 }).devices.size)
        assertThrows(IllegalArgumentException::class.java) { BatterySnapshotV1.parse(bytes + ByteArray(65537 - bytes.size) { 32 }) }
        reject("[".repeat(17) + "0" + "]".repeat(17))
        // Each non-ASCII character consumes multiple bytes; use byte counts, never String.length.
        reject(change("\"g915\"", "\"" + "é".repeat(33000) + "\""))
        assertNotNull(StrictJson("[".repeat(16) + "0" + "]".repeat(16)).read())
    }
    @Test fun utcTimestampEvidenceOnly() {
        val real = change("\"synthetic\": true", "\"synthetic\": false")
        listOf("2026-10-31T12:00:00Z", "2026-10-31T12:00:00.1234567Z").forEach {
            assertEquals(Instant.parse(it), parse(change("\"capturedAt\": null", "\"capturedAt\": \"$it\"", real)).capturedAt)
        }
        listOf("2026-10-31T12:00:00", "2026-10-31T12:00:00+00:00", "2026-02-30T12:00:00Z", "0000-01-01T00:00:00Z", "2026-10-31T24:00:00Z", "2026-10-31T12:00:00.12345678Z").forEach {
            reject(change("\"capturedAt\": null", "\"capturedAt\": \"$it\"", real))
        }
        reject(change("\"readAt\": null", "\"readAt\": \"2026-10-31T12:00:00Z\""))
        val stale = fixture("golden/stale-last-known.json").toString(Charsets.UTF_8)
        reject(stale.replace("\"readAt\": null", "\"readAt\": \"2026-10-31T12:00:00Z\""))
    }
    @Test fun lastKnownHasSameSafetyConstraintsAsCurrent() {
        val stale = fixture("golden/stale-last-known.json").toString(Charsets.UTF_8)
        reject(change("\"batteryPercent\": 99", "\"batteryPercent\": 101", stale))
        reject(change("\"precision\": \"PRECISE\"", "\"precision\": \"UNKNOWN\", \"precision\": \"PRECISE\"", stale))
        val liveStale = stale.replace("\"synthetic\": true", "\"synthetic\": false")
        // Change observedAt on every reading, including retained GHUB_GATEWAY evidence.
        reject(liveStale.replace("\"observedAt\": null", "\"observedAt\": \"2026-10-31T12:00:00Z\""))
    }
    @Test fun hyperxEvidenceCannotBeUpgraded() {
        reject(change("\"HYPERX_SUPERVISED\"", "\"UNKNOWN\""))
        reject(change("\"ESTIMATE\"", "\"PRECISE\""))
        reject(change("\"AMBIGUOUS\"", "\"ATTRIBUTED\""))
        val projection = parse(demo).projectOffline()
        assertEquals(Precision.ESTIMATE, projection.snapshot.devices[2].precision)
        assertEquals(Ownership.AMBIGUOUS, projection.evidence.devices[2].current.source.ownership)
    }
    @Test fun staleCacheNeverBecomesCurrent() {
        val p = BatterySnapshotV1.parse(fixture("golden/stale-last-known.json")).projectOffline()
        assertNull(p.snapshot.devices[0].batteryPercent)
        assertEquals(99, p.evidence.devices[0].lastKnown?.batteryPercent)
        assertEquals(Availability.STALE, p.snapshot.devices[0].availability)
        assertEquals(Freshness.STALE, p.evidence.devices[0].freshness)
    }
    @Test fun zeroUnknownUnsupportedAndConnectionAreDistinct() {
        val p = BatterySnapshotV1.parse(fixture("golden/zero-and-unknown.json")).projectOffline()
        assertEquals(0, p.snapshot.devices[0].batteryPercent)
        assertNull(p.snapshot.devices[1].batteryPercent)
        val unsupported = change("\"availability\": \"AVAILABLE\"", "\"availability\": \"NOT_SUPPORTED\"", change("\"batteryPercent\": 99", "\"batteryPercent\": null"))
        val d = parse(unsupported).projectOffline()
        assertEquals(WireAvailability.NOT_SUPPORTED, d.evidence.devices[0].availability)
        assertNotEquals(Availability.DEVICE_DISCONNECTED, d.snapshot.devices[0].availability)
        val unknown = parse(change("\"precision\": \"PRECISE\"", "\"precision\": \"UNKNOWN\"")).projectOffline()
        assertEquals("Unknown", unknown.snapshot.devices[0].present().value)
        listOf(parse(demo), parse(change("\"synthetic\": true", "\"synthetic\": false"))).forEach {
            val s = it.projectOffline().snapshot
            assertFalse(s.pcOnline); assertEquals(it.synthetic, s.synthetic)
            assertEquals(if (it.synthetic) ConnectionState.DEMO else ConnectionState.DISCONNECTED, s.connection.state)
            assertFalse(s.forUi().pcOnline)
        }
    }
    @Test fun immutableEvidenceAndObservableImportWithoutDefaultChanges() {
        val repository = OfflineSnapshotRepository(fixture("golden/demo.json"))
        val old = repository.projections.value
        assertThrows(UnsupportedOperationException::class.java) { (old.evidence.devices as MutableList).clear() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val vm = BatteryViewModel(repository, scope)
            repository.importSnapshot(fixture("golden/zero-and-unknown.json"))
            assertEquals(0, vm.state.value.snapshot.devices[0].batteryPercent)
            assertEquals(99, old.evidence.devices[0].current.batteryPercent)
            val before = repository.snapshot()
            assertThrows(IllegalArgumentException::class.java) { repository.importSnapshot(fixture("rejected/version.json")) }
            assertSame(before, repository.snapshot())
            assertEquals(listOf(99, 90, 76), DemoBatteryRepository().snapshot().devices.map { it.batteryPercent })
        } finally { scope.cancel() }
    }
}
