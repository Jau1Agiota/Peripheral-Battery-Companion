package dev.peripheral.battery.contract

import dev.peripheral.battery.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Wire evidence remains available independently of the narrower Android presentation model. */
data class OfflineSnapshotProjection(val evidence: BatterySnapshotV1, val snapshot: BatterySnapshot)

fun BatterySnapshotV1.projectOffline(): OfflineSnapshotProjection {
    val snapshot = BatterySnapshot(
        devices = devices.map { d ->
            DeviceReading(
                identity = d.id, batteryPercent = d.current.batteryPercent, precision = d.current.precision,
                // Unavailable/unsupported do not prove a device disconnect. No cached value becomes current.
                availability = if (d.availability == WireAvailability.STALE) Availability.STALE else Availability.AVAILABLE,
                source = d.current.source.kind.name, observedAt = d.current.observedAt,
                caution = listOfNotNull("Offline snapshot · No live connection", d.unavailableReason?.name,
                    if (d.current.source.ownership == Ownership.AMBIGUOUS) "Source ownership ambiguous" else null).joinToString(" · "),
            )
        },
        connection = ConnectionMetadata(if (synthetic) ConnectionState.DEMO else ConnectionState.DISCONNECTED, "Offline snapshot"),
        capturedAt = capturedAt,
    )
    return OfflineSnapshotProjection(this, snapshot)
}

/** Explicit, in-memory import only. Never installed as the app's default provider. */
class OfflineSnapshotRepository(initialPayload: ByteArray) : BatteryRepository {
    private val imported = MutableStateFlow(BatterySnapshotV1.parse(initialPayload).projectOffline())
    val projections = imported.asStateFlow()
    private val state = MutableStateFlow(imported.value.snapshot)
    override val snapshots = state.asStateFlow()
    @Synchronized fun importSnapshot(payload: ByteArray) {
        val next = BatterySnapshotV1.parse(payload).projectOffline()
        imported.value = next
        state.value = next.snapshot
    }
}
