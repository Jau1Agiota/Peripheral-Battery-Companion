package dev.peripheral.battery.data

import dev.peripheral.battery.domain.*

class DemoBatteryRepository : BatteryRepository {
    override fun snapshot() = BatterySnapshot(
        devices = listOf(
            DeviceReading("g915", "G915 Keyboard", 99, Precision.PRECISE, source = "G HUB"),
            DeviceReading("g305", "G305 Mouse", 90, Precision.APPROXIMATE, source = "G HUB"),
            DeviceReading("hyperx", "HyperX Cloud III Wireless", 76, Precision.ESTIMATE,
                source = "Unvalidated sample", caution = "Estimate · source under validation"),
        ), synthetic = true, pcOnline = false,
    )
}
