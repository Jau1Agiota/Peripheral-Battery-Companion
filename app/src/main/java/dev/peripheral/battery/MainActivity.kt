package dev.peripheral.battery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.peripheral.battery.domain.*
import dev.peripheral.battery.ui.*

private val Cyan = Color(0xFF41DDF5)
private val Muted = Color(0xFFB2C4DE)
private val Ink = Color(0xFF050B12)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Ink,
                surface = Color(0xFF101B29), onSurface = Color(0xFFF3F6FC), onBackground = Color(0xFFF3F6FC))) {
                val model: BatteryViewModel = viewModel()
                val state by model.state.collectAsStateWithLifecycle()
                CompanionApp(state, model::refreshDemo)
            }
        }
    }
}

@Composable
fun CompanionApp(state: BatteryUiState, onRefresh: () -> Unit) {
    var settings by rememberSaveable { mutableStateOf(false) }
    Scaffold(containerColor = Ink, bottomBar = {
        NavigationBar(containerColor = Ink) {
            NavigationBarItem(selected = !settings, onClick = { settings = false },
                icon = { Text("▣", Modifier.clearAndSetSemantics {}) }, label = { Text("Devices") })
            NavigationBarItem(selected = settings, onClick = { settings = true },
                icon = { Text("⚙", Modifier.clearAndSetSemantics {}) }, label = { Text("Settings") })
        }
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("ϟ  Peripheral Battery Companion", style = MaterialTheme.typography.headlineSmall)
            Text("Your devices, at a glance", color = Muted)
            if (settings) {
                Text("Advanced settings", style = MaterialTheme.typography.headlineMedium)
                Text("Demo only", color = Cyan, style = MaterialTheme.typography.titleLarge)
                Text("All readings are synthetic. No PC is connected. Refresh only reloads the same sample values.")
                Text("Future connectivity", style = MaterialTheme.typography.titleLarge)
                Text("LAN and authenticated VPN access are future proposals. Pairing, authentication, encryption and Windows API review remain unresolved. No network service, discovery, polling or notifications are enabled.")
                TextButton(onClick = { settings = false }) { Text("Back to Devices") }
            } else {
                Surface(shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Cyan.copy(alpha = .4f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Demo mode · Not connected", color = Cyan, style = MaterialTheme.typography.titleMedium)
                        Text("Synthetic sample readings · Demo data", color = Muted)
                        Text("These values do not reflect your devices.", color = Muted)
                    }
                }
                state.snapshot.devices.forEach { DeviceCard(it) }
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh demo") }
                if (state.refreshCount > 0) Text("Demo reloaded ${state.refreshCount} time(s) · Sample values unchanged",
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = Muted)
                OutlinedButton(onClick = { settings = true }, modifier = Modifier.fillMaxWidth()) { Text("Advanced settings") }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceReading) {
    val p = device.present()
    val accent = if (device.id == "hyperx") Color(0xFFC9576C) else Cyan
    val resource = when (device.id) { "g915" -> R.drawable.keyboard; "g305" -> R.drawable.mouse; else -> R.drawable.headset }
    Surface(shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, accent.copy(alpha = .65f))) {
        BoxWithConstraints(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(accent.copy(alpha = .12f), Ink))).padding(16.dp)) {
            val stacked = maxWidth < 320.dp || LocalDensity.current.fontScale > 1.2f
            val picture: @Composable (Modifier) -> Unit = { modifier ->
                Image(painterResource(resource), contentDescription = "${device.name} illustration",
                    modifier = modifier.height(150.dp), contentScale = ContentScale.Fit)
            }
            val reading: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(device.name, style = MaterialTheme.typography.titleLarge)
                    Text(p.value, style = MaterialTheme.typography.headlineMedium)
                    p.progress?.let { progress ->
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp)
                            .semantics { contentDescription = "${device.name} battery" },
                            color = if (device.precision == Precision.ESTIMATE) Color(0xFFFFDA52) else Color(0xFF53E8B5),
                            trackColor = Color(0xFF263348))
                    }
                    Text(p.description, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    Text("Demo data", color = Muted, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (stacked) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                picture(Modifier.fillMaxWidth()); reading(Modifier.fillMaxWidth())
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                picture(Modifier.weight(.43f)); reading(Modifier.weight(.57f))
            }
        }
    }
}
