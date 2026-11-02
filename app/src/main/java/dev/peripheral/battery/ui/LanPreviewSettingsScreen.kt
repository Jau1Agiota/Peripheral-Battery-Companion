package dev.peripheral.battery.ui
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import dev.peripheral.battery.data.*
@Composable
fun LanPreviewSettingsScreen(permittedHost: String, connect: (String, Int) -> Unit, disconnect: () -> Unit) {
    var host by rememberSaveable { mutableStateOf(permittedHost) }
    var port by rememberSaveable { mutableStateOf("8765") }
    var error by remember { mutableStateOf<String?>(null) }
    Column {
        Text(LAN_PREVIEW_LABEL)
        OutlinedTextField(host, { host = it }, label = { Text("PC private IPv4") }, singleLine = true)
        OutlinedTextField(port, { port = it }, label = { Text("Port") }, singleLine = true)
        Button(onClick = {
            try {
                val settings = LanPreviewSettings(host, port.toInt())
                require(settings.host == permittedHost)
                connect(settings.host, settings.port); error = null
            } catch (_: IllegalArgumentException) { error = "Use the build-approved PC address and port 1024–65535." }
        }) { Text("Connect LAN preview") }
        TextButton(onClick = disconnect) { Text("Disconnect LAN preview") }
        error?.let { Text(it) }
    }
}
