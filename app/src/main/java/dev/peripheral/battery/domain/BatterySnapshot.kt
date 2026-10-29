package dev.peripheral.battery.domain

import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

enum class DeviceType { KEYBOARD, MOUSE, HEADSET }
enum class DeviceIdentity(val stableId: String, val displayName: String, val type: DeviceType) {
    G915("g915", "G915 Keyboard", DeviceType.KEYBOARD),
    G305("g305", "G305 Mouse", DeviceType.MOUSE),
    HYPERX("hyperx", "HyperX Cloud III Wireless", DeviceType.HEADSET),
}
enum class Precision { PRECISE, APPROXIMATE, ESTIMATE, UNKNOWN }
enum class Availability { AVAILABLE, STALE, DEVICE_DISCONNECTED, PC_OFFLINE }

/** Confidence describes evidence, not a device's presumed accuracy. Unknown is never precise. */
data class DeviceReading(
    val identity: DeviceIdentity,
    val batteryPercent: Int?,
    val precision: Precision,
    val availability: Availability = Availability.AVAILABLE,
    val source: String? = null,
    val observedAt: Instant? = null,
    val caution: String? = null,
) {
    val id: String get() = identity.stableId
    val name: String get() = identity.displayName
    init { require(batteryPercent == null || batteryPercent in 0..100) }
}

/** CONNECTED means a future provider has established a genuine connection; never inferred from readings. */
enum class ConnectionState { DEMO, DISCONNECTED, CONNECTING, CONNECTED, STALE, ERROR }
data class ConnectionMetadata(val state: ConnectionState, val message: String? = null)

data class BatterySnapshot(
    val devices: List<DeviceReading>,
    val connection: ConnectionMetadata,
    val capturedAt: Instant? = null,
) {
    val synthetic: Boolean get() = connection.state == ConnectionState.DEMO
    val pcOnline: Boolean get() = connection.state == ConnectionState.CONNECTED
    init {
        require(devices.map { it.identity }.distinct().size == devices.size) { "Duplicate device identity" }
        if (synthetic) {
            require(capturedAt == null && devices.all { it.observedAt == null }) { "Demo must not invent timestamps" }
        }
    }
}

/** Provider owns its observable snapshots. No transport or Windows protocol is specified here. */
interface BatteryRepository {
    val snapshots: StateFlow<BatterySnapshot>
    fun snapshot(): BatterySnapshot = snapshots.value
    /** Explicit local reload only. Future networking must use a separately reviewed operation. */
    fun refreshDemo() = Unit
}
