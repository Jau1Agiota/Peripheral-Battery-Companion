package dev.peripheral.battery.debugharness

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.peripheral.battery.MainActivity

/** Debug-only second launcher. Intent extras never select a scenario or bypass the opt-in screen. */
class DebugHarnessActivity : ComponentActivity() {
    private val model: DebugHarnessModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val inputs = HarnessInputs(HarnessFixture.entries.associateWith { fixture ->
                    assets.open(fixture.assetPath).use { it.readBytes() }
                })
                @Suppress("UNCHECKED_CAST")
                return DebugHarnessModel(inputs) as T
            }
        })[DebugHarnessModel::class.java]
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val state by model.state.collectAsStateWithLifecycle()
                BackHandler { exitToDemo() }
                DebugHarnessScreen(state, model::enterOffline, model::enterSession,
                    model::advanceOffline, model::act, model::reset, ::exitToDemo)
            }
        }
    }
    override fun onStop() {
        if (!isChangingConfigurations) model.pause()
        super.onStop()
    }
    private fun exitToDemo() {
        model.exit()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@Composable
fun DebugHarnessScreen(
    state: HarnessUiState,
    offline: () -> Unit,
    session: () -> Unit,
    advance: () -> Unit,
    act: (HarnessAction) -> Unit,
    reset: () -> Unit,
    exit: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.warning, Modifier.testTag("mock-warning"), style = MaterialTheme.typography.titleMedium)
            Text("DEBUG ONLY · Local test data · Gate E BLOCKED", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = reset, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Reset local engines") }
                OutlinedButton(onClick = exit, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Exit to normal demo") }
            }
            LazyColumn(Modifier.weight(1f).testTag("harness-list"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    if (state.route == HarnessRoute.LANDING) {
                        Text("Explicit opt-in required. No PC, credentials, network, or real authentication. Choose a local debug mode below.")
                    }
                    Button(onClick = offline, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open OFFLINE fixture simulator") }
                    Button(onClick = session, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open MOCK session policy") }
                }
                if (state.route != HarnessRoute.LANDING) {
                    item {
                        Text(state.status, Modifier.testTag("mock-status"))
                        Text(state.outcome, Modifier.testTag("mock-outcome"))
                        Text(state.publication)
                    }
                    if (state.route == HarnessRoute.OFFLINE) item {
                        Button(onClick = advance, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Next local fixture") }
                    }
                    items(state.cards) { card ->
                        OutlinedCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = card.accessibilityLabel }) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(card.name, style = MaterialTheme.typography.titleMedium)
                                Text(card.current)
                                Text(card.availability)
                                Text(card.retained)
                                Text(card.source)
                                Text(card.times)
                            }
                        }
                    }
                    if (state.route == HarnessRoute.SESSION) {
                        item { Text("Actions are local and explicit. Begin → approve fake session → bind → receive. A policy rejection requires a new explicit begin.") }
                        items(HarnessAction.entries) { action ->
                            OutlinedButton(onClick = { act(action) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(action.label) }
                        }
                    }
                }
            }
        }
    }
}
