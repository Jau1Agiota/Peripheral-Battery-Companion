package dev.peripheral.battery

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.peripheral.battery.data.DemoBatteryRepository
import dev.peripheral.battery.ui.BatteryUiState
import org.junit.Rule
import org.junit.Test

class CompanionUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun demoCardsAndNavigation() {
        compose.setContent { MaterialTheme { CompanionApp(BatteryUiState(DemoBatteryRepository().snapshot()), {}) } }
        compose.onNodeWithText("Demo mode · Not connected").assertExists()
        compose.onNodeWithText("G915 Keyboard").assertExists()
        compose.onNodeWithText("~90%").assertExists()
        compose.onNodeWithText("Battery estimate: 76%").assertExists()
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Future connectivity").assertExists()
        compose.onNodeWithText("Back to Devices").performClick()
        compose.onNodeWithText("Demo mode · Not connected").assertExists()
    }
}
