package dev.peripheral.battery

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.peripheral.battery.debugharness.DebugHarnessActivity
import dev.peripheral.battery.debugharness.HarnessAction
import org.junit.Rule
import org.junit.Test

/** Debug instrumentation only. Compilation is not device execution. */
class DebugHarnessUiTest {
    @get:Rule val compose = createAndroidComposeRule<DebugHarnessActivity>()
    private fun click(label: String) {
        compose.onNodeWithTag("harness-list").performScrollToNode(hasText(label))
        compose.onNodeWithText(label).performClick()
    }
    private fun status(text: String) {
        compose.onNodeWithTag("harness-list").performScrollToNode(hasTestTag("mock-status"))
        compose.onNodeWithTag("mock-status").assertTextContains(text, substring = true)
    }
    @Test fun optInAndOfflineZeroSurviveActivityRecreation() {
        compose.onNodeWithText("Explicit opt-in required.", substring = true).assertExists()
        compose.onNodeWithTag("mock-status").assertDoesNotExist()
        click("Open OFFLINE fixture simulator")
        click("Next local fixture"); click("Next local fixture")
        status("ZERO_VERSUS_NULL")
        compose.activityRule.scenario.recreate()
        status("ZERO_VERSUS_NULL")
        compose.onNodeWithTag("mock-warning").assertTextContains("MOCK / OFFLINE", substring = true)
        compose.onNodeWithTag("harness-list").performScrollToNode(hasContentDescription("G915 Keyboard. Displayed current: 0%", substring = true))
        compose.onNodeWithContentDescription("G915 Keyboard. Displayed current: 0%", substring = true).assertExists()
    }
    @Test fun mockConnectionIsExplicitAndResetDoesNotClaimConnection() {
        click("Open MOCK session policy")
        listOf(HarnessAction.BEGIN, HarnessAction.AUTHENTICATE, HarnessAction.BIND, HarnessAction.ONE).forEach { click(it.label) }
        status("MOCK PC session · CONNECTED · No real PC connected")
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitForIdle()
        status("MOCK PC session · CONNECTED")
        compose.onNodeWithText("Reset local engines").performClick()
        status("DISCONNECTED")
        compose.onNodeWithTag("mock-warning").assertExists()
    }
    @Test fun exitReturnsToUnchangedNormalDemo() {
        click("Open MOCK session policy")
        compose.onNodeWithText("Exit to normal demo").performClick()
        compose.onNodeWithText("~90%").assertExists()
        compose.onNodeWithText("Demo mode · Not connected").performScrollTo().assertExists()
        compose.onNodeWithTag("mock-warning").assertDoesNotExist()
    }
}
