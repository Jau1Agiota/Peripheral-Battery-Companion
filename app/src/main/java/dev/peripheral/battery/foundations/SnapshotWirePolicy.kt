package dev.peripheral.battery.foundations

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.util.UUID

/** Unwired wire metadata reader; keeps all fields, never maps lossy data into demo UI models.
 * Platform JSONObject parsing is preceded by a strict lexical/duplicate/depth guard.
 * This is not authentication, transport or a general JSON Schema validator.
 */
object SnapshotWirePolicy {
    const val MAX_BYTES = 65536
    fun read(bytes: ByteArray): JSONObject {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        StrictJsonGuard(text).validate()
        val root = JSONObject(text)
        shape(root, "schemaVersion synthetic serverInstanceId revision capturedAt devices")
        require(integer(root.get("schemaVersion")) == 1L)
        val id = root.get("serverInstanceId") as String
        require(UUID.fromString(id).toString() == id && id != "00000000-0000-0000-0000-000000000000")
        require(integer(root.get("revision")) >= 0)
        val synthetic = root.get("synthetic") as Boolean
        time(root.get("capturedAt"), synthetic)
        val devices = root.getJSONArray("devices"); require(devices.length() == 3)
        listOf("g915", "g305", "hyperx").forEachIndexed { i, idValue ->
            val d = devices.getJSONObject(i)
            shape(d, "id current availability freshness deviceConnection unavailableReason lastKnown")
            require(d.get("id") == idValue)
            one(d, "availability", "AVAILABLE UNAVAILABLE STALE NOT_SUPPORTED")
            one(d, "freshness", "RECENT_READ STALE UNKNOWN")
            one(d, "deviceConnection", "ACTIVE_REPORTED ABSENT_REPORTED UNKNOWN")
            if (!d.isNull("unavailableReason")) one(d, "unavailableReason",
                "NO_READING SOURCE_UNAVAILABLE DEVICE_ABSENT STALE_CACHE AMBIGUOUS_TARGET SAFETY_STOP MONITORING_OFF UNSUPPORTED")
            val current = d.getJSONObject("current"); reading(current, synthetic, i == 2)
            val number = !current.isNull("batteryPercent")
            require((d.get("availability") == "AVAILABLE") == number)
            require(!number || d.isNull("unavailableReason"))
            if (!d.isNull("lastKnown")) reading(d.getJSONObject("lastKnown"), synthetic, i == 2)
        }
        return root // no provenance, receipt, lastKnown or enum fields discarded
    }
    private fun integer(value: Any): Long {
        require(value is Int || value is Long)
        return (value as Number).toLong()
    }
    private fun shape(obj: JSONObject, fields: String) {
        require(obj.keys().asSequence().toSet() == fields.split(' ').toSet())
    }
    private fun one(obj: JSONObject, field: String, values: String) { require(obj.get(field) is String && obj.get(field) in values.split(' ')) }
    private fun time(value: Any, synthetic: Boolean) {
        if (value == JSONObject.NULL) return
        require(!synthetic && value is String && value.endsWith('Z'))
        Instant.parse(value as String)
    }
    private fun reading(r: JSONObject, synthetic: Boolean, hyperx: Boolean) {
        shape(r, "batteryPercent precision source readAt observedAt")
        if (!r.isNull("batteryPercent")) require(integer(r.get("batteryPercent")) in 0..100)
        one(r, "precision", "PRECISE APPROXIMATE ESTIMATE UNKNOWN")
        val s = r.getJSONObject("source"); shape(s, "kind socSupport ownership")
        one(s, "kind", "GHUB_GATEWAY HYPERX_SUPERVISED GHUB_STORED UNKNOWN")
        one(s, "socSupport", "SUPPORTED NOT_SUPPORTED UNKNOWN NOT_APPLICABLE")
        one(s, "ownership", "ATTRIBUTED AMBIGUOUS UNKNOWN")
        val kind = s.get("kind")
        require(!hyperx || r.isNull("batteryPercent") || kind == "HYPERX_SUPERVISED")
        require(kind != "HYPERX_SUPERVISED" || r.get("precision") == "ESTIMATE" && s.get("ownership") == "AMBIGUOUS")
        require(kind !in listOf("GHUB_GATEWAY", "HYPERX_SUPERVISED") || r.isNull("observedAt"))
        time(r.get("readAt"), synthetic); time(r.get("observedAt"), synthetic)
    }
}
