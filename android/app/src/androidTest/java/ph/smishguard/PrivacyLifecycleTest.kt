package ph.smishguard

import android.os.Bundle
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PrivacyLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun backgroundAndRecreateDiscardText() {
        val app = compose.activity.application as SmishGuardApp
        runBlocking { app.preferences.onboard() }
        compose.waitForIdle()
        compose.onNodeWithText("Check a message").performScrollTo().performClick()
        compose.onNodeWithText("SMS message").performTextInput("SYNTHETIC_PRIVATE_SESSION_MARKER")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("SYNTHETIC_PRIVATE_SESSION_MARKER").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("SYNTHETIC_PRIVATE_SESSION_MARKER").assertDoesNotExist()
        compose.activityRule.scenario.onActivity {
            assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
    }
}
