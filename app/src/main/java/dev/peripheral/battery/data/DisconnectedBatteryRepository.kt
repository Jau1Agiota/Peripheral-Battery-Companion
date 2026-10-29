package dev.peripheral.battery.data

import dev.peripheral.battery.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Safe empty foundation for a future provider: no transport, clock, credentials or battery guesses. */
class DisconnectedBatteryRepository : BatteryRepository {
    override val snapshots = MutableStateFlow(BatterySnapshot(
        devices = DeviceIdentity.entries.map {
            DeviceReading(it, null, Precision.UNKNOWN, Availability.PC_OFFLINE)
        },
        connection = ConnectionMetadata(ConnectionState.DISCONNECTED),
    )).asStateFlow()
}
