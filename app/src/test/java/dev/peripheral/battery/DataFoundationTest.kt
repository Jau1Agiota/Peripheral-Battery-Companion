package dev.peripheral.battery

import dev.peripheral.battery.data.*
import dev.peripheral.battery.domain.*
import dev.peripheral.battery.ui.*
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.*
import org.junit.Test

class DataFoundationTest {
    private val sample = DemoBatteryRepository().snapshot()
    private val knownTime = Instant.parse("2026-10-01T12:00:00Z")
    private fun live(state: ConnectionState, devices: List<DeviceReading> = emptyList()) =
        BatterySnapshot(devices, ConnectionMetadata(state))

    @Test fun defaultMappingPreservesApprovedDemoContent() {
        assertEquals(sample, sample.forUi())
        assertEquals("Demo data · Synthetic sample readings", sample.connection.present().banner)
        assertEquals("Demo data", sample.connection.present().readingLabel)
        assertEquals("Synthetic samples · Not your device readings", sample.connection.present().detail)
        assertEquals(listOf(99, 90, 76), sample.forUi().devices.map { it.batteryPercent })
    }
    @Test fun stableIdentityIncludesTypeAndCanonicalOrder() {
        assertEquals(listOf("g915", "g305", "hyperx"), DeviceIdentity.entries.map { it.stableId })
        assertEquals(listOf(DeviceType.KEYBOARD, DeviceType.MOUSE, DeviceType.HEADSET), DeviceIdentity.entries.map { it.type })
        assertEquals(DeviceIdentity.entries, sample.devices.map { it.identity })
    }
    @Test fun disconnectedProviderContainsEveryIdentityWithoutInventedEvidence() {
        val snapshot = DisconnectedBatteryRepository().snapshot()
        assertEquals(ConnectionState.DISCONNECTED, snapshot.connection.state)
        assertFalse(snapshot.synthetic); assertFalse(snapshot.pcOnline); assertNull(snapshot.capturedAt)
        assertEquals(DeviceIdentity.entries, snapshot.devices.map { it.identity })
        snapshot.devices.forEach {
            assertNull(it.batteryPercent); assertNull(it.observedAt); assertNull(it.source)
            assertEquals(Precision.UNKNOWN, it.precision)
            assertEquals("Unknown", it.present().value); assertNull(it.present().progress)
        }
    }
    @Test fun missingLiveReadingsNeverBecomeSamples() {
        val mapped = live(ConnectionState.CONNECTED).forUi()
        assertTrue(mapped.pcOnline); assertFalse(mapped.synthetic)
        assertEquals(3, mapped.devices.size)
        mapped.devices.forEach { assertNull(it.batteryPercent); assertNull(it.observedAt); assertNull(it.present().progress) }
    }
    @Test fun unknownAndZeroRemainDistinct() {
        val knownZero = sample.devices.first().copy(batteryPercent = 0)
        assertEquals("0%", knownZero.present().value); assertEquals(0f, knownZero.present().progress)
        val missing = knownZero.copy(batteryPercent = null)
        assertEquals("Unknown", missing.present().value); assertNull(missing.present().progress)
    }
    @Test fun unknownConfidenceDoesNotBecomePreciseOrDrawProgress() {
        val unknown = sample.devices.first().copy(precision = Precision.UNKNOWN)
        assertEquals("Unknown", unknown.present().value)
        assertNull(unknown.present().progress)
        assertEquals("Reading confidence unknown", unknown.present().description)
    }
    @Test fun allConfidenceValuesPropagateWithoutUpgrade() {
        Precision.entries.forEach { confidence ->
            val reading = sample.devices.first().copy(precision = confidence)
            val mapped = live(ConnectionState.CONNECTED, listOf(reading)).forUi().devices.first()
            assertEquals(confidence, mapped.precision)
        }
    }
    @Test fun actualSourceAndTimestampsSurviveMapping() {
        val reading = sample.devices.first().copy(source = "Evidence supplied by provider", observedAt = knownTime)
        val mapped = live(ConnectionState.CONNECTED, listOf(reading)).copy(capturedAt = knownTime).forUi()
        assertEquals(knownTime, mapped.capturedAt)
        assertEquals(reading, mapped.devices.first())
    }
    @Test fun mappingReordersAndFillsWithoutChangingExistingEvidence() {
        val headset = sample.devices.last()
        val keyboard = sample.devices.first()
        val mapped = live(ConnectionState.CONNECTED, listOf(headset, keyboard)).forUi()
        assertEquals(listOf(keyboard, mapped.devices[1], headset), mapped.devices)
        assertEquals(DeviceIdentity.G305, mapped.devices[1].identity)
        assertNull(mapped.devices[1].batteryPercent)
        assertEquals(Precision.UNKNOWN, mapped.devices[1].precision)
    }
    @Test fun disconnectedAndConnectingDoNotDisplayCachedCharge() {
        listOf(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING).forEach { state ->
            val reading = live(state, sample.devices).forUi().devices.first()
            assertNull(reading.batteryPercent); assertNull(reading.present().progress)
            assertEquals("Unknown", reading.present().value)
        }
    }
    @Test fun staleAndErrorMarkCachedValuesAsStaleWithoutChangingConfidence() {
        listOf(ConnectionState.STALE, ConnectionState.ERROR).forEach { state ->
            val mapped = live(state, sample.devices).forUi()
            mapped.devices.zip(sample.devices).forEach { (actual, original) ->
                assertEquals(Availability.STALE, actual.availability)
                assertEquals(original.precision, actual.precision)
                assertTrue(actual.present().description.contains("Stale reading"))
            }
        }
    }
    @Test fun staleConnectionDoesNotOverwriteDeviceDisconnection() {
        val unavailable = sample.devices.first().copy(availability = Availability.DEVICE_DISCONNECTED)
        val mapped = live(ConnectionState.STALE, listOf(unavailable)).forUi().devices.first()
        assertEquals(Availability.DEVICE_DISCONNECTED, mapped.availability)
        assertNull(mapped.present().progress)
    }
    @Test fun eachConnectionStateHasExplicitTruthfulPresentation() {
        val expected = mapOf(
            ConnectionState.DEMO to "Demo mode · Not connected",
            ConnectionState.DISCONNECTED to "Not connected",
            ConnectionState.CONNECTING to "Connecting",
            ConnectionState.CONNECTED to "Connected",
            ConnectionState.STALE to "Connection stale",
            ConnectionState.ERROR to "Connection error",
        )
        ConnectionState.entries.forEach { state ->
            assertEquals(expected[state], ConnectionMetadata(state).present().status)
            assertEquals(state == ConnectionState.CONNECTED, live(state).pcOnline)
        }
    }
    @Test fun demoAndEmptyProvidersNeverClaimConnectedOrSupplyTimestamps() {
        listOf(DemoBatteryRepository(), DisconnectedBatteryRepository()).forEach { repository ->
            repository.refreshDemo()
            val snapshot = repository.snapshot()
            assertFalse(snapshot.pcOnline); assertNull(snapshot.capturedAt)
            assertTrue(snapshot.devices.all { it.observedAt == null })
            assertNotEquals("Connected", snapshot.connection.present().status)
        }
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateIdentityIsRejected() {
        live(ConnectionState.CONNECTED, listOf(sample.devices.first(), sample.devices.first()))
    }
    @Test(expected = IllegalArgumentException::class) fun demoRejectsFabricatedSnapshotTime() {
        sample.copy(capturedAt = knownTime)
    }
    @Test(expected = IllegalArgumentException::class) fun demoRejectsFabricatedReadingTime() {
        sample.copy(devices = listOf(sample.devices.first().copy(observedAt = knownTime)))
    }
    @Test fun providerUpdatesReachObservableUiWithoutRefreshOrSampleFallback() {
        val repository = MutableProvider(live(ConnectionState.DISCONNECTED))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val vm = BatteryViewModel(repository, scope)
            assertEquals(3, vm.state.value.snapshot.devices.size)
            ConnectionState.entries.forEach { connection ->
                val reading = sample.devices.first().copy(batteryPercent = 0)
                repository.emit(live(connection, listOf(reading)))
                val ui = vm.state.value
                assertEquals(connection, ui.snapshot.connection.state)
                assertEquals(0, ui.refreshCount)
                assertEquals(if (connection in listOf(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING)) null else 0,
                    ui.snapshot.devices.first().batteryPercent)
                assertNull(ui.snapshot.devices[1].batteryPercent)
            }
        } finally { scope.cancel() }
    }
    @Test fun refreshReloadsDemoOnlyAndKeepsConfirmationCount() {
        val repository = MutableProvider(sample)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val vm = BatteryViewModel(repository, scope)
            vm.refreshDemo(); vm.refreshDemo()
            assertEquals(2, vm.state.value.refreshCount); assertEquals(2, repository.reloads)
            repository.emit(live(ConnectionState.DISCONNECTED))
            vm.refreshDemo()
            assertEquals(2, vm.state.value.refreshCount); assertEquals(2, repository.reloads)
            assertFalse(vm.state.value.snapshot.synthetic)
        } finally { scope.cancel() }
    }
    @Test fun cancelledObservationDoesNotKeepApplyingProviderUpdates() {
        val repository = MutableProvider(sample)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val vm = BatteryViewModel(repository, scope)
        scope.cancel()
        repository.emit(live(ConnectionState.ERROR))
        assertEquals(ConnectionState.DEMO, vm.state.value.snapshot.connection.state)
    }
    private class MutableProvider(initial: BatterySnapshot) : BatteryRepository {
        private val mutable = MutableStateFlow(initial)
        override val snapshots = mutable.asStateFlow()
        var reloads = 0
        fun emit(value: BatterySnapshot) { mutable.value = value }
        override fun refreshDemo() { reloads++ }
    }
}
