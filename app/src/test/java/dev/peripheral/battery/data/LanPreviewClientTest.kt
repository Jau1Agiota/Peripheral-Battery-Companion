package dev.peripheral.battery.data

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LanPreviewClientTest {
    private class Connection(private val bytes: ByteArray, private val code: Int = 200,
        private val type: String = "application/json") : HttpURLConnection(URL("http://192.168.2.1:8765/v1/battery-snapshot")) {
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getContentType() = type
        override fun getInputStream() = ByteArrayInputStream(bytes)
    }
    @Test fun boundedJsonReadDisablesRedirectsAndDisconnects() = runBlocking {
        val c = Connection("{}".toByteArray())
        assertArrayEquals("{}".toByteArray(), LanPreviewClient("192.168.2.1") { c }.fetch(LanPreviewSettings("192.168.2.1")))
        assertFalse(c.instanceFollowRedirects); assertTrue(c.closed); assertFalse(c.useCaches)
    }
    @Test fun redirectsErrorsWrongTypeAndOversizeDeny() {
        for (c in listOf(Connection(byteArrayOf(), 302), Connection(byteArrayOf(), 503),
            Connection("{}".toByteArray(), type="text/html"), Connection(ByteArray(65537)), Connection(byteArrayOf()))) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking {
                LanPreviewClient("192.168.2.1") { c }.fetch(LanPreviewSettings("192.168.2.1"))
            } }
            assertTrue(c.closed)
        }
    }
}
