package dev.peripheral.battery

import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.peripheral.battery.contract.BatterySnapshotV1
import dev.peripheral.battery.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit real-LAN test only; needs an owner-enabled Windows preview. No fixture fallback. */
@RunWith(AndroidJUnit4::class)
class LanPreviewNetworkTest {
    @Test fun numericIpv4PolicyIsNarrow() {
        val policy = NetworkSecurityPolicy.getInstance()
        assertTrue(BuildConfig.LAN_PREVIEW_HOST.isNotEmpty())
        assertTrue(policy.isCleartextTrafficPermitted(BuildConfig.LAN_PREVIEW_HOST))
        assertFalse(policy.isCleartextTrafficPermitted("8.8.8.8"))
        assertFalse(policy.isCleartextTrafficPermitted("192.168.254.254"))
    }
    @Test fun actualWindowsPublicationParsesOnPhone() = runBlocking {
        val snapshot = BatterySnapshotV1.parse(LanPreviewClient(BuildConfig.LAN_PREVIEW_HOST).fetch(LanPreviewSettings(BuildConfig.LAN_PREVIEW_HOST)))
        assertFalse(snapshot.synthetic)
        assertEquals(3, snapshot.devices.size)
        snapshot.devices.forEach { println("LAN_DEVICE ${it.id.stableId}: ${it.current.batteryPercent} ${it.current.precision} ${it.availability}") }
    }
}
