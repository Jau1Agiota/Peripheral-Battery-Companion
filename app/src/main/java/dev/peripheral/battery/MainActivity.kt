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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.peripheral.battery.domain.*
import dev.peripheral.battery.ui.*

private val Cyan = Color(0xFF00D8F5)
private val Muted = Color(0xFFADC0DD)
private val Ink = Color(0xFF03080D)
private val Green = Color(0xFF10EE43)
private val Yellow = Color(0xFFFFDD00)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Ink,
                surface = Color(0xFF0A1420), onSurface = Color(0xFFF4F7FC), onBackground = Color(0xFFF4F7FC))) {
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
        Column(Modifier.background(Ink)) {
            HorizontalDivider(color = Color(0xFF123047))
            NavigationBar(containerColor = Ink, tonalElevation = 0.dp) {
                val colors = NavigationBarItemDefaults.colors(selectedIconColor = Cyan,
                    selectedTextColor = Cyan, indicatorColor = Cyan.copy(alpha = .07f),
                    unselectedIconColor = Muted, unselectedTextColor = Muted)
                NavigationBarItem(selected = !settings, onClick = { settings = false }, colors = colors,
                    icon = { LineIcon(Glyph.Devices, if (!settings) Cyan else Muted) }, label = { Text("Devices", fontSize = 12.sp) })
                NavigationBarItem(selected = false, enabled = false, onClick = {}, colors = colors,
                    modifier = Modifier.semantics { contentDescription = "History, coming soon" },
                    icon = { LineIcon(Glyph.History, Muted.copy(alpha = .45f)) }, label = { Text("History · Soon", fontSize = 11.sp) })
                NavigationBarItem(selected = settings, onClick = { settings = true }, colors = colors,
                    icon = { LineIcon(Glyph.Settings, if (settings) Cyan else Muted) }, label = { Text("Settings", fontSize = 12.sp) })
            }
        }
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineIcon(Glyph.BoltBattery, Cyan, Modifier.size(34.dp, 46.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Peripheral Battery Companion", fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold)
                    Text("Your devices, at a glance", color = Muted, fontSize = 12.sp)
                }
                OutlinedIconButton(onClick = { settings = true }, shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF075078)), modifier = Modifier.size(48.dp)) {
                    LineIcon(Glyph.Settings, Muted, Modifier.size(23.dp).semantics { contentDescription = "Open settings" })
                }
            }
            if (settings) {
                Text("Advanced settings", style = MaterialTheme.typography.headlineSmall)
                Text("Demo only", color = Cyan, style = MaterialTheme.typography.titleLarge)
                Text("All readings are synthetic. No PC is connected. Refresh only reloads the same sample values.")
                Text("Future connectivity", style = MaterialTheme.typography.titleLarge)
                Text("LAN and authenticated VPN access are future proposals. Pairing, authentication, encryption and Windows API review remain unresolved. No network service, discovery, polling or notifications are enabled.")
                TextButton(onClick = { settings = false }) { Text("Back to Devices") }
            } else {
                Text("Demo data · Synthetic sample readings", color = Muted, fontSize = 10.sp)
                state.snapshot.devices.forEach { DeviceCard(it) }
                Box {
                    val large = LocalDensity.current.fontScale > 1.5f
                    val buttons: @Composable (Modifier) -> Unit = { modifier ->
                        ActionButton("Refresh", Glyph.Refresh, onRefresh, modifier)
                    }
                    if (large) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        buttons(Modifier.fillMaxWidth())
                        ActionButton("Advanced settings", Glyph.Settings, { settings = true }, Modifier.fillMaxWidth())
                    } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        buttons(Modifier.weight(1f))
                        ActionButton("Advanced settings", Glyph.Settings, { settings = true }, Modifier.weight(1.15f))
                    }
                }
                if (state.refreshCount > 0) Text("Demo reloaded ${state.refreshCount} time(s) · Sample values unchanged",
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = Muted, fontSize = 12.sp)
                Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF13415C)), color = Ink) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LineIcon(Glyph.Monitor, Muted, Modifier.size(30.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("PC Connection", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Demo mode · Not connected", color = Cyan, fontSize = 12.sp)
                            Text("Synthetic samples · Not your device readings", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, glyph: Glyph, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        border = BorderStroke(1.dp, Color(0xFF0078AA)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
        LineIcon(glyph, Cyan, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun DeviceCard(device: DeviceReading) {
    val p = device.present()
    val accent = when (device.id) { "hyperx" -> Color(0xFFD23854); "g305" -> Color(0xFF688CAB); else -> Color(0xFF00ADEC) }
    val resource = when (device.id) { "g915" -> R.drawable.keyboard; "g305" -> R.drawable.mouse; else -> R.drawable.headset }
    val chargeColor = if (device.precision == Precision.ESTIMATE) Yellow else Green
    Surface(shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, accent.copy(alpha = .85f)), color = Ink) {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(accent.copy(alpha = .22f), Ink)))) {
            val largeFont = LocalDensity.current.fontScale > 1.5f
            Row(Modifier.fillMaxWidth().heightIn(min = 136.dp).padding(horizontal = 9.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Image(painterResource(resource), contentDescription = "${device.name} illustration",
                    modifier = Modifier.weight(if (largeFont) .32f else .46f).height(if (largeFont) 145.dp else 122.dp),
                    contentScale = ContentScale.Fit)
                Column(Modifier.weight(if (largeFont) .68f else .54f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(device.name, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
                        // Decorative cue, not a false interactive control.
                        LineIcon(Glyph.Chevron, Muted, Modifier.size(14.dp).padding(start = 4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${device.name}: ${p.value}" }) {
                        LineIcon(Glyph.Battery, chargeColor, Modifier.size(15.dp, 24.dp))
                        Text(if (device.precision == Precision.ESTIMATE && device.batteryPercent != null && p.progress != null)
                            "${device.batteryPercent}%" else p.value,
                            fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    p.progress?.let { progress ->
                        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1D293B)).padding(2.dp)
                            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f); contentDescription = "${device.name} battery" }) {
                            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(chargeColor))
                        }
                    }
                    Text(if (device.precision == Precision.ESTIMATE && p.progress != null) p.value else "Demo data",
                        color = Muted, fontSize = 11.sp, lineHeight = 14.sp)
                    Text(p.description, color = Muted, fontSize = 11.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}
