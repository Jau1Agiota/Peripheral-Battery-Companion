package dev.peripheral.battery.ui

import dev.peripheral.battery.domain.*

data class ConnectionPresentation(val banner: String, val status: String, val detail: String, val readingLabel: String)
fun ConnectionMetadata.present(): ConnectionPresentation = when (state) {
    ConnectionState.DEMO -> ConnectionPresentation("Demo data · Synthetic sample readings", "Demo mode · Not connected", "Synthetic samples · Not your device readings", "Demo data")
    ConnectionState.DISCONNECTED -> ConnectionPresentation("No connection · Readings unavailable", "Not connected", "No battery readings available", "No observation available")
    ConnectionState.CONNECTING -> ConnectionPresentation("Connecting · Readings unavailable", "Connecting", "Waiting for a genuine connection", "No observation available")
    ConnectionState.CONNECTED -> ConnectionPresentation("Provider readings", "Connected", "Connection reported by provider", "Provider data")
    ConnectionState.STALE -> ConnectionPresentation("Stale data · Not current", "Connection stale", "Cached readings may be outdated", "Stale data")
    ConnectionState.ERROR -> ConnectionPresentation("Connection error · Not current", "Connection error", "Readings unavailable or cached", "Connection error")
}

/** Canonical card order and missing identities are maintained regardless of provider order/completeness. */
fun BatterySnapshot.forUi(): BatterySnapshot {
    val offline = connection.state == ConnectionState.DISCONNECTED || connection.state == ConnectionState.CONNECTING
    val stale = connection.state == ConnectionState.STALE || connection.state == ConnectionState.ERROR
    val byIdentity = devices.associateBy { it.identity }
    return copy(devices = DeviceIdentity.entries.map { identity ->
        val reading = byIdentity[identity] ?: DeviceReading(identity, null, Precision.UNKNOWN, Availability.DEVICE_DISCONNECTED)
        when {
            offline -> reading.copy(batteryPercent = null, availability = Availability.PC_OFFLINE)
            stale && reading.availability == Availability.AVAILABLE -> reading.copy(availability = Availability.STALE)
            else -> reading
        }
    })
}
