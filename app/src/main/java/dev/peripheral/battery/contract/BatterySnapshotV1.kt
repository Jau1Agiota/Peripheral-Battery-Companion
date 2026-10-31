package dev.peripheral.battery.contract

import dev.peripheral.battery.domain.DeviceIdentity
import dev.peripheral.battery.domain.Precision
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Collections
import java.util.UUID

enum class SourceKind { GHUB_GATEWAY, HYPERX_SUPERVISED, GHUB_STORED, UNKNOWN }
enum class SocSupport { SUPPORTED, NOT_SUPPORTED, UNKNOWN, NOT_APPLICABLE }
enum class Ownership { ATTRIBUTED, AMBIGUOUS, UNKNOWN }
enum class WireAvailability { AVAILABLE, UNAVAILABLE, STALE, NOT_SUPPORTED }
enum class Freshness { RECENT_READ, STALE, UNKNOWN }
enum class DeviceConnection { ACTIVE_REPORTED, ABSENT_REPORTED, UNKNOWN }
enum class UnavailableReason { NO_READING, SOURCE_UNAVAILABLE, DEVICE_ABSENT, STALE_CACHE, AMBIGUOUS_TARGET, SAFETY_STOP, MONITORING_OFF, UNSUPPORTED }
data class SnapshotSource(val kind: SourceKind, val socSupport: SocSupport, val ownership: Ownership)
data class SnapshotReading(val batteryPercent: Int?, val precision: Precision, val source: SnapshotSource, val readAt: Instant?, val observedAt: Instant?)
data class SnapshotDevice(val id: DeviceIdentity, val current: SnapshotReading, val availability: WireAvailability, val freshness: Freshness, val deviceConnection: DeviceConnection, val unavailableReason: UnavailableReason?, val lastKnown: SnapshotReading?)

/** Only the strict reader can construct a validated snapshot; the collection is defensively immutable. */
class BatterySnapshotV1 private constructor(
    val schemaVersion: Int, val synthetic: Boolean, val serverInstanceId: UUID, val revision: Long,
    val capturedAt: Instant?, devices: List<SnapshotDevice>,
) {
    val devices: List<SnapshotDevice> = Collections.unmodifiableList(ArrayList(devices))
    companion object {
        const val MAX_PAYLOAD_BYTES = 65536
        fun parse(bytes: ByteArray): BatterySnapshotV1 {
            require(bytes.size <= MAX_PAYLOAD_BYTES) { "Payload exceeds 65536 bytes" }
            val text = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
                catch (e: java.nio.charset.CharacterCodingException) { throw IllegalArgumentException("Invalid UTF-8", e) }
            val root = fields(StrictJson(text).read(), "schemaVersion", "synthetic", "serverInstanceId", "revision", "capturedAt", "devices")
            require(integer(root["schemaVersion"]) == 1L)
            val synthetic = root["synthetic"] as? Boolean ?: throw IllegalArgumentException("Expected boolean")
            val uuid = root["serverInstanceId"] as? String ?: throw IllegalArgumentException("Expected UUID")
            require(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}").matches(uuid))
            val identity = UUID.fromString(uuid); require(identity != UUID(0, 0))
            val revision = integer(root["revision"]); require(revision >= 0)
            val capturedAt = time(root["capturedAt"], synthetic)
            val devices = (root["devices"] as? List<*>)?.map { device(it, synthetic) } ?: throw IllegalArgumentException("Expected devices")
            require(devices.map { it.id } == DeviceIdentity.entries.toList()) { "Canonical device order required" }
            return BatterySnapshotV1(1, synthetic, identity, revision, capturedAt, devices)
        }
        private fun device(value: Any?, synthetic: Boolean): SnapshotDevice {
            val d = fields(value, "id", "current", "availability", "freshness", "deviceConnection", "unavailableReason", "lastKnown")
            val id = DeviceIdentity.entries.singleOrNull { it.stableId == d["id"] } ?: throw IllegalArgumentException("Invalid device")
            val current = reading(d["current"], synthetic)
            val availability = enum<WireAvailability>(d["availability"])
            val lastKnown = d["lastKnown"]?.let { reading(it, synthetic) }
            val reason = d["unavailableReason"]?.let { enum<UnavailableReason>(it) }
            require(if (availability == WireAvailability.AVAILABLE) current.batteryPercent != null && reason == null else current.batteryPercent == null)
            if (id == DeviceIdentity.HYPERX) listOfNotNull(current, lastKnown).forEach {
                require(it.batteryPercent == null || it.source.kind == SourceKind.HYPERX_SUPERVISED)
            }
            return SnapshotDevice(id, current, availability, enum(d["freshness"]), enum(d["deviceConnection"]), reason, lastKnown)
        }
        private fun reading(value: Any?, synthetic: Boolean): SnapshotReading {
            val r = fields(value, "batteryPercent", "precision", "source", "readAt", "observedAt")
            val percent = r["batteryPercent"]?.let { integer(it).also { n -> require(n in 0..100) }.toInt() }
            val precision = enum<Precision>(r["precision"])
            val s = fields(r["source"], "kind", "socSupport", "ownership")
            val source = SnapshotSource(enum(s["kind"]), enum(s["socSupport"]), enum(s["ownership"]))
            val readAt = time(r["readAt"], synthetic); val observedAt = time(r["observedAt"], synthetic)
            if (source.kind == SourceKind.HYPERX_SUPERVISED) require(precision == Precision.ESTIMATE && source.ownership == Ownership.AMBIGUOUS && observedAt == null)
            if (source.kind == SourceKind.GHUB_GATEWAY) require(observedAt == null)
            return SnapshotReading(percent, precision, source, readAt, observedAt)
        }
        private fun fields(value: Any?, vararg names: String): Map<*, *> {
            val map = value as? Map<*, *> ?: throw IllegalArgumentException("Expected object")
            require(map.keys == names.toSet()) { "Missing or unknown field" }; return map
        }
        private inline fun <reified T : Enum<T>> enum(value: Any?): T = enumValues<T>().singleOrNull { it.name == value } ?: throw IllegalArgumentException("Unknown enum")
        private fun integer(value: Any?): Long {
            val n = value as? JsonNumber ?: throw IllegalArgumentException("Expected integer")
            require(Regex("-?(0|[1-9][0-9]*)").matches(n.text))
            return n.text.toLongOrNull() ?: throw IllegalArgumentException("Integer overflow")
        }
        private fun time(value: Any?, synthetic: Boolean): Instant? {
            if (value == null) return null
            require(!synthetic)
            val s = value as? String ?: throw IllegalArgumentException("Expected UTC timestamp")
            // C# DateTimeKind.Utc is serialized with Z, not a local/offset DateTime.
            require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,7})?Z").matches(s))
            return try { LocalDateTime.parse(s.dropLast(1)).also { require(it.year in 1..9999) }.toInstant(ZoneOffset.UTC) }
                catch (e: java.time.DateTimeException) { throw IllegalArgumentException("Invalid UTC timestamp", e) }
        }
    }
}
