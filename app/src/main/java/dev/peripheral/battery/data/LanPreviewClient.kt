package dev.peripheral.battery.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import dev.peripheral.battery.contract.BatterySnapshotV1

fun interface LanPreviewFetch { suspend fun fetch(settings: LanPreviewSettings): ByteArray }

class LanPreviewClient(private val permittedHost: String,
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : LanPreviewFetch {
    override suspend fun fetch(settings: LanPreviewSettings): ByteArray = withTimeout(3000) { withContext(Dispatchers.IO) {
        require(settings.host == permittedHost) { "Address not permitted by this preview build" }
        suspendCancellableCoroutine { continuation ->
            val connection = open(URL(settings.url))
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 2500; connection.readTimeout = 2500
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.useCaches = false
            continuation.invokeOnCancellation { connection.disconnect() }
            try {
                require(connection.responseCode == 200) { "HTTP response unavailable" }
                require(connection.contentType?.substringBefore(';')?.trim() == "application/json") { "Unexpected response type" }
                val bytes = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    while (output.size() <= BatterySnapshotV1.MAX_PAYLOAD_BYTES) {
                        val count = input.read(buffer, 0, minOf(buffer.size, BatterySnapshotV1.MAX_PAYLOAD_BYTES + 1 - output.size()))
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                require(bytes.isNotEmpty() && bytes.size <= BatterySnapshotV1.MAX_PAYLOAD_BYTES) { "Payload bound" }
                continuation.resumeWith(Result.success(bytes))
            } catch (error: Exception) { continuation.resumeWith(Result.failure(error)) }
            finally { connection.disconnect() }
        }
    }
} }
