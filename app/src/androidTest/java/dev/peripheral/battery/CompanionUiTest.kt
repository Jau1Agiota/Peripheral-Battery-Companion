package dev.peripheral.battery

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.peripheral.battery.data.DisconnectedBatteryRepository
import dev.peripheral.battery.ui.forUi
import dev.peripheral.battery.data.DemoBatteryRepository
import dev.peripheral.battery.ui.BatteryUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompanionUiTest {
    @get:Rule val compose = createComposeRule()
    private val state = BatteryUiState(DemoBatteryRepository().snapshot())

    @Test fun demoCardsAndNavigation() {
        compose.setContent { MaterialTheme { CompanionApp(state, {}) } }
        val keyboard = compose.onNodeWithText("G915 Keyboard").fetchSemanticsNode().boundsInRoot
        val mouse = compose.onNodeWithText("G305 Mouse").fetchSemanticsNode().boundsInRoot
        val headset = compose.onNodeWithText("HyperX Cloud III Wireless").fetchSemanticsNode().boundsInRoot
        assertTrue(keyboard.top < mouse.top && mouse.top < headset.top)
        compose.onNodeWithText("~90%").assertExists()
        compose.onNodeWithText("Battery estimate: 76%").assertExists()
        compose.onNodeWithText("Demo mode · Not connected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("History, coming soon").assertIsNotEnabled()
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Future connectivity").assertExists()
        compose.onNodeWithText("Back to Devices").performScrollTo().performClick()
        compose.onNodeWithText("G915 Keyboard").performScrollTo().assertIsDisplayed()
    }

    @Test fun emptyProviderKeepsCardsAndDoesNotPretendToBeDemoOrConnected() {
        val disconnected = DisconnectedBatteryRepository().snapshot().forUi()
        compose.setContent { MaterialTheme { CompanionApp(BatteryUiState(disconnected), {}) } }
        compose.onNodeWithText("G915 Keyboard").assertExists()
        compose.onNodeWithText("G305 Mouse").assertExists()
        compose.onNodeWithText("HyperX Cloud III Wireless").assertExists()
        compose.onAllNodesWithText("Unknown").assertCountEquals(3)
        compose.onNodeWithText("Refresh").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Not connected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Demo mode · Not connected").assertDoesNotExist()
        compose.onNodeWithText("Connected").assertDoesNotExist()
    }

    @Test fun narrowLargeFontsRemainScrollable() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                MaterialTheme { Box(Modifier.width(320.dp)) { CompanionApp(state, {}) } }
            }
        }
        compose.onNodeWithText("HyperX Cloud III Wireless").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Demo mode · Not connected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Advanced settings").performScrollTo().performClick()
        compose.onNodeWithText("Future connectivity").performScrollTo().assertIsDisplayed()
    }
}
