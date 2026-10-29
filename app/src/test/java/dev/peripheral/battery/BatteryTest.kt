package dev.peripheral.battery

import dev.peripheral.battery.domain.*
import dev.peripheral.battery.data.DemoBatteryRepository
import dev.peripheral.battery.ui.present
import org.junit.Assert.*
import org.junit.Test

class BatteryTest {
    private val sample = DemoBatteryRepository().snapshot()
    @Test fun demoIsExplicitlyDisconnected() {
        assertTrue(sample.synthetic); assertFalse(sample.pcOnline)
        assertEquals(listOf("g915", "g305", "hyperx"), sample.devices.map { it.id })
        assertTrue(sample.devices.all { it.observedAt == null })
    }
    @Test fun preciseWording() { assertEquals("99%", sample.devices[0].present().value); assertTrue(sample.devices[0].present().description.contains("G HUB · Precise reading")) }
    @Test fun approximateWording() { assertEquals("~90%", sample.devices[1].present().value) }
    @Test fun estimateWording() { assertEquals("Battery estimate: 76%", sample.devices[2].present().value) }
    @Test fun nullIsUnknown() { val p = sample.devices[0].copy(batteryPercent = null).present(); assertEquals("Unknown", p.value); assertNull(p.progress) }
    @Test fun offlineDoesNotShowCachedCharge() {
        for (state in listOf(Availability.PC_OFFLINE, Availability.DEVICE_DISCONNECTED)) {
            val p = sample.devices[0].copy(availability = state).present()
            assertEquals("Unknown", p.value); assertNull(p.progress)
        }
    }
    @Test fun staleIsLabeled() { assertTrue(sample.devices[0].copy(availability = Availability.STALE).present().description.contains("Stale")) }
    @Test fun progressEndpoints() { assertEquals(0f, sample.devices[0].copy(batteryPercent = 0).present().progress); assertEquals(1f, sample.devices[0].copy(batteryPercent = 100).present().progress) }
    @Test(expected = IllegalArgumentException::class) fun rejectsNegative() { sample.devices[0].copy(batteryPercent = -1) }
    @Test(expected = IllegalArgumentException::class) fun rejectsOverflow() { sample.devices[0].copy(batteryPercent = 101) }
}
