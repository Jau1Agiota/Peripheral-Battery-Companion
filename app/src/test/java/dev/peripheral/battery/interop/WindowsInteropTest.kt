package dev.peripheral.battery.interop

import dev.peripheral.battery.contract.*
import dev.peripheral.battery.domain.*
import dev.peripheral.battery.ui.forUi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.security.MessageDigest
import java.time.Instant

/** Inputs were constructed in C#; accepted publication bytes come from actual Core serialization. */
internal object E2Vectors {
    val policyDifferences = setOf("policy-uppercase-uuid", "policy-negative-zero")
    fun publicationExpectation(case: Map<*, *>): Map<*, *>? {
        require(case["androidAccepts"] == true) { "Expected an accepted test case" }
        if (case["id"] in policyDifferences) {
            assertEquals("known-policy-difference", case["group"])
            assertEquals(false, case["windowsAccepts"])
            assertNull(case["expected"])
            return null
        }
        assertEquals(true, case["windowsAccepts"])
        assertTrue(case["group"] in setOf("independent-input", "publication-sequence"))
        return requireNotNull(case["expected"] as? Map<*, *>) { "Accepted publication requires a semantic expectation object" }
    }
    fun bytes(file: String): ByteArray = requireNotNull(javaClass.getResourceAsStream("/interop/e2/$file")).use { it.readBytes() }
    private fun tree(file: String) = StrictJson(bytes(file).toString(Charsets.UTF_8)).read()
    val manifest get() = tree("manifest.json") as Map<*, *>
    val cases get() = manifest["cases"] as List<*>
    fun case(id: String) = cases.map { it as Map<*, *> }.single { it["id"] == id }
    fun parse(id: String) = BatterySnapshotV1.parse(bytes(case(id)["file"] as String))
    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    fun number(value: Any?) = (value as JsonNumber).text.toLong()
    fun time(value: Any?) = (value as? String)?.let(Instant::parse)
}

@RunWith(Parameterized::class)
class WindowsInteropTest(private val id: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "Windows E2: {0}")
        fun cases(): List<Array<String>> = E2Vectors.cases.map { arrayOf((it as Map<*, *>)["id"] as String) }
    }
    @Test fun exactWindowsEmittedBytesMatchProducerHash() {
        val file = E2Vectors.case(id)["file"] as String
        val expected = E2Vectors.bytes("SHA256SUMS").toString(Charsets.UTF_8).lineSequence()
            .single { it.endsWith("  $file") }.substringBefore("  ")
        assertEquals(expected, E2Vectors.hash(E2Vectors.bytes(file)))
    }
    @Test fun kotlinAcceptanceAndEveryEvidenceFieldMatchWindows() {
        val case = E2Vectors.case(id)
        val bytes = E2Vectors.bytes(case["file"] as String)
        if (case["androidAccepts"] == false) {
            assertThrows(IllegalArgumentException::class.java) { BatterySnapshotV1.parse(bytes) }
            return
        }
        val snapshot = BatterySnapshotV1.parse(bytes)
        assertEquals(1, snapshot.schemaVersion); assertFalse(snapshot.synthetic)
        if (id in E2Vectors.policyDifferences) {
            assertNull(E2Vectors.publicationExpectation(case))
            when (id) {
                "policy-uppercase-uuid" -> assertEquals("aabbccdd-1234-4000-8000-000000000001", snapshot.serverInstanceId.toString())
                "policy-negative-zero" -> assertEquals(0L, snapshot.revision)
            }
            assertEquals(ConnectionState.DISCONNECTED, snapshot.projectOffline().snapshot.connection.state)
            return
        }
        val expected = requireNotNull(E2Vectors.publicationExpectation(case))
        assertEquals(expected["serverInstanceId"], snapshot.serverInstanceId.toString())
        assertEquals(E2Vectors.number(expected["revision"]), snapshot.revision)
        assertEquals(E2Vectors.time(expected["capturedAt"]), snapshot.capturedAt)
        val devices = expected["devices"] as List<*>
        assertEquals(3, snapshot.devices.size)
        snapshot.devices.forEachIndexed { index, actual ->
            val d = devices[index] as Map<*, *>
            assertEquals(d["id"], actual.id.stableId)
            assertEquals(d["availability"], actual.availability.name)
            assertEquals(d["freshness"], actual.freshness.name)
            assertEquals(d["deviceConnection"], actual.deviceConnection.name)
            assertEquals(d["unavailableReason"], actual.unavailableReason?.name)
            reading(d["current"] as Map<*, *>, actual.current)
            val retained = d["lastKnown"] as? Map<*, *>
            if (retained == null) assertNull(actual.lastKnown) else reading(retained, requireNotNull(actual.lastKnown))
        }
        val projection = snapshot.projectOffline()
        assertFalse(projection.snapshot.pcOnline)
        assertEquals(ConnectionState.DISCONNECTED, projection.snapshot.connection.state)
        assertTrue(projection.snapshot.forUi().devices.all { it.batteryPercent == null })
    }
    private fun reading(expected: Map<*, *>, actual: SnapshotReading) {
        assertEquals(expected["batteryPercent"]?.let(E2Vectors::number)?.toInt(), actual.batteryPercent)
        assertEquals(expected["precision"], actual.precision.name)
        val source = expected["source"] as Map<*, *>
        assertEquals(source["kind"], actual.source.kind.name)
        assertEquals(source["socSupport"], actual.source.socSupport.name)
        assertEquals(source["ownership"], actual.source.ownership.name)
        assertEquals(E2Vectors.time(expected["readAt"]), actual.readAt)
        assertEquals(E2Vectors.time(expected["observedAt"]), actual.observedAt)
    }
}

class WindowsInteropSemanticsTest {
    @Test fun missingOrMistypedPublicationExpectationsFailRatherThanSkip() {
        val original = E2Vectors.case("precise-both-hyperx-estimate")
        for (bad in listOf(null, "not an object", listOf("not an object"))) {
            val changed = original.toMutableMap().also { it["expected"] = bad }
            assertThrows(IllegalArgumentException::class.java) { E2Vectors.publicationExpectation(changed) }
        }
    }
    @Test fun unknownPolicyIdsCannotBypassPublicationAssertions() {
        val changed = E2Vectors.case("policy-uppercase-uuid").toMutableMap().also { it["id"] = "policy-unreviewed" }
        assertThrows(AssertionError::class.java) { E2Vectors.publicationExpectation(changed) }
    }
    @Test fun namedPolicyCasesMustRetainExplicitMetadataAssertions() {
        val original = E2Vectors.case("policy-negative-zero")
        assertThrows(AssertionError::class.java) {
            E2Vectors.publicationExpectation(original.toMutableMap().also { it["expected"] = emptyMap<String, Any>() })
        }
        assertThrows(AssertionError::class.java) {
            E2Vectors.publicationExpectation(original.toMutableMap().also { it["windowsAccepts"] = true })
        }
    }
    @Test fun manifestIsExplicitlyControlledInputNotLiveTelemetry() {
        assertEquals("E2-1", E2Vectors.manifest["suite"])
        assertEquals("CONTROLLED TEST INPUT - NOT LIVE TELEMETRY", E2Vectors.manifest["inputKind"])
        assertEquals(false, E2Vectors.manifest["wireSynthetic"])
        assertEquals(27, E2Vectors.cases.size)
        val cases = E2Vectors.cases.map { it as Map<*, *> }
        assertEquals(14, cases.count { it["expected"] != null && it["windowsAccepts"] == true })
        assertEquals(11, cases.count { it["group"] == "negative-mutation" })
        assertEquals(2, cases.count { it["group"] == "known-policy-difference" })
    }
    @Test fun repeatedReadsAndPublisherRestartHaveCorrectScopes() {
        val first = E2Vectors.parse("sequence-first")
        val repeated = E2Vectors.parse("sequence-repeat")
        val second = E2Vectors.parse("sequence-second")
        val restarted = E2Vectors.parse("sequence-restarted")
        assertArrayEquals(E2Vectors.bytes("sequence-first.json"), E2Vectors.bytes("sequence-repeat.json"))
        assertEquals(first.revision, repeated.revision)
        assertEquals(first.capturedAt, repeated.capturedAt)
        assertEquals(first.serverInstanceId, second.serverInstanceId)
        assertEquals(first.revision + 1, second.revision)
        assertEquals(first.capturedAt!!.plusNanos(100), second.capturedAt)
        assertNotEquals(second.serverInstanceId, restarted.serverInstanceId)
        assertEquals(1L, restarted.revision)
    }
    @Test fun zeroNullStaleAndUnsupportedDoNotLoseMeaning() {
        assertTrue(E2Vectors.parse("zero-all").devices.all { it.current.batteryPercent == 0 })
        assertTrue(E2Vectors.parse("null-unavailable-all").devices.all { it.current.batteryPercent == null })
        assertTrue(E2Vectors.parse("stale-lastknown-all").devices.all { it.current.batteryPercent == null && it.lastKnown?.batteryPercent != null })
        assertTrue(E2Vectors.parse("unsupported-both").devices.take(2).all { it.availability == WireAvailability.NOT_SUPPORTED && it.current.batteryPercent == null })
        assertTrue(E2Vectors.parse("approximate-both").devices.take(2).all { it.availability == WireAvailability.AVAILABLE && it.current.source.socSupport == SocSupport.NOT_SUPPORTED && it.current.precision == Precision.APPROXIMATE })
    }
    @Test fun sourceTimesAndHyperxConfidenceSurviveActualSerializer() {
        val gateway = E2Vectors.parse("precise-both-hyperx-estimate")
        assertEquals(Instant.parse("2026-10-31T12:00:00.7654321Z"), gateway.capturedAt)
        assertEquals(Instant.parse("2026-10-31T11:59:00.1234567Z"), gateway.devices[0].current.readAt)
        assertNull(gateway.devices[0].current.observedAt)
        assertEquals(Ownership.AMBIGUOUS, gateway.devices[2].current.source.ownership)
        assertEquals(Precision.ESTIMATE, gateway.devices[2].current.precision)
        assertEquals(SourceKind.GHUB_STORED, E2Vectors.parse("legacy-g915-observation").devices[0].current.source.kind)
        assertNotNull(E2Vectors.parse("legacy-g915-observation").devices[0].current.observedAt)
        assertTrue(E2Vectors.parse("null-receipt-times").devices.take(2).all { it.current.readAt == null })
        assertTrue(E2Vectors.parse("unknown-confidence-both").devices.take(2).all { it.current.precision == Precision.UNKNOWN })
    }
    @Test fun measuredPolicyDifferencesAreExplicitAndDoNotModifyEitherParser() {
        for (id in listOf("policy-uppercase-uuid", "policy-negative-zero")) {
            val case = E2Vectors.case(id)
            assertEquals(false, case["windowsAccepts"])
            assertEquals(true, case["androidAccepts"])
            assertEquals("known-policy-difference", case["group"])
            assertNotNull(E2Vectors.parse(id))
        }
    }
}
