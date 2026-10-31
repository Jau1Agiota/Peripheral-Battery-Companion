package dev.peripheral.battery.contract

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

internal fun fixture(name: String): ByteArray = requireNotNull(FixtureParityTest::class.java.getResourceAsStream("/contracts/$name")).use { it.readBytes() }

@RunWith(Parameterized::class)
class FixtureParityTest(private val file: String, private val accept: Boolean) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}: accept={1}")
        fun cases(): List<Array<Any>> {
            val scenarios = StrictJson(fixture("scenarios.json").toString(Charsets.UTF_8)).read() as List<*>
            require(scenarios.size == 17 && scenarios.count { (it as Map<*, *>)["accept"] == true } == 4)
            return scenarios.map { val s = it as Map<*, *>; arrayOf(s["file"] as String, s["accept"] as Boolean) }
        }
    }
    @Test fun matchesCanonicalOutcome() {
        if (accept) assertEquals(3, BatterySnapshotV1.parse(fixture(file)).devices.size)
        else assertThrows(IllegalArgumentException::class.java) { BatterySnapshotV1.parse(fixture(file)) }
    }
    @Test fun matchesCanonicalHash() {
        val line = fixture("SHA256SUMS").toString(Charsets.UTF_8).lineSequence().single { it.endsWith("  $file") }
        val hash = MessageDigest.getInstance("SHA-256").digest(fixture(file)).joinToString("") { "%02x".format(it) }
        assertEquals(line.substringBefore("  "), hash)
    }
}
