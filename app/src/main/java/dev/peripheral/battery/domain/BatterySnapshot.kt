package dev.peripheral.battery.domain

import java.time.Instant

enum class Precision { PRECISE, APPROXIMATE, ESTIMATE }
enum class Availability { AVAILABLE, STALE, DEVICE_DISCONNECTED, PC_OFFLINE }
data class DeviceReading(
    val id: String,
    val name: String,
    val batteryPercent: Int?,
    val precision: Precision,
    val availability: Availability = Availability.AVAILABLE,
    val source: String,
    val observedAt: Instant? = null,
    val caution: String? = null,
) {
    init { require(batteryPercent == null || batteryPercent in 0..100) }
}
data class BatterySnapshot(val devices: List<DeviceReading>, val synthetic: Boolean, val pcOnline: Boolean)
interface BatteryRepository { fun snapshot(): BatterySnapshot }
