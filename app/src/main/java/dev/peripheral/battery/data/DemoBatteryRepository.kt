package dev.peripheral.battery.data

import dev.peripheral.battery.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DemoBatteryRepository : BatteryRepository {
    private val samples = BatterySnapshot(
        devices = listOf(
            DeviceReading(DeviceIdentity.G915, 99, Precision.PRECISE, source = "G HUB"),
            DeviceReading(DeviceIdentity.G305, 90, Precision.APPROXIMATE, source = "G HUB"),
            DeviceReading(DeviceIdentity.HYPERX, 76, Precision.ESTIMATE,
                source = "Unvalidated sample", caution = "Estimate · source under validation"),
        ), connection = ConnectionMetadata(ConnectionState.DEMO),
    )
    private val mutableSnapshots = MutableStateFlow(samples)
    override val snapshots = mutableSnapshots.asStateFlow()
    override fun refreshDemo() { mutableSnapshots.value = samples }
}
