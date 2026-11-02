package dev.peripheral.battery.data
import dev.peripheral.battery.contract.*
import dev.peripheral.battery.domain.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
const val LAN_PREVIEW_LABEL = "LAN preview · unencrypted and unauthenticated."
/** Transport receipt never upgrades device freshness, precision or response ownership. */
class LanPreviewRepository(
    private val client: LanPreviewFetch,
    private val wait: suspend (Long) -> Unit = { delay(it) },
    private val nanoTime: () -> Long = System::nanoTime,
) : BatteryRepository {
    private val mutable = MutableStateFlow(BatterySnapshot(emptyList(), ConnectionMetadata(ConnectionState.DISCONNECTED, LAN_PREVIEW_LABEL)))
    override val snapshots = mutable.asStateFlow()
    private val active = AtomicBoolean(false)
    suspend fun monitor(settings: LanPreviewSettings) {
        check(active.compareAndSet(false, true)) { "One flight only" }
        var failures = 0
        var lastId: java.util.UUID? = null; var lastRevision = -1L; var advancedAt = nanoTime()
        try {
            mutable.value = mutable.value.copy(connection = ConnectionMetadata(ConnectionState.CONNECTING, LAN_PREVIEW_LABEL))
            while (currentCoroutineContext().isActive) {
                try {
                    val wire = BatterySnapshotV1.parse(client.fetch(settings))
                    require(!wire.synthetic) { "Demo payload cannot become live data" }
                    require(wire.serverInstanceId != lastId || wire.revision >= lastRevision) { "Regressed publication" }
                    if (wire.serverInstanceId != lastId || wire.revision > lastRevision) advancedAt = nanoTime()
                    lastId = wire.serverInstanceId; lastRevision = wire.revision
                    val stalePublication = nanoTime() - advancedAt >= 30_000_000_000L
                    mutable.value = BatterySnapshot(wire.devices.map { d ->
                        val reading = if (d.availability == WireAvailability.STALE) d.lastKnown ?: d.current else d.current
                        DeviceReading(d.id, reading.batteryPercent, reading.precision,
                            if (stalePublication || d.availability == WireAvailability.STALE) Availability.STALE else Availability.AVAILABLE,
                            reading.source.kind.name, reading.readAt ?: reading.observedAt,
                            listOfNotNull(d.unavailableReason?.name, if (reading.source.ownership == Ownership.AMBIGUOUS) "Source ownership ambiguous" else null).joinToString(" · "))
                    }, ConnectionMetadata(if (stalePublication) ConnectionState.STALE else ConnectionState.CONNECTED, LAN_PREVIEW_LABEL), wire.capturedAt)
                    failures = 0
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    failures++
                    mutable.value = mutable.value.copy(connection = ConnectionMetadata(
                        if (mutable.value.devices.isEmpty()) ConnectionState.ERROR else ConnectionState.STALE, LAN_PREVIEW_LABEL))
                }
                wait(if (failures == 0) 2000 else when (failures) { 1 -> 2000; 2 -> 5000; else -> 15000 })
            }
        } finally {
            active.set(false)
            mutable.value = mutable.value.copy(connection = ConnectionMetadata(
                if (mutable.value.devices.isEmpty()) ConnectionState.DISCONNECTED else ConnectionState.STALE, LAN_PREVIEW_LABEL))
        }
    }
}
