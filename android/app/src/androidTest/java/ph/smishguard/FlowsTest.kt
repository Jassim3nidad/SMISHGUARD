package ph.smishguard

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FlowsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun resetPreferences() {
        val app = compose.activity.application as SmishGuardApp
        runBlocking { app.preferences.clear(); app.preferences.onboard() }
        compose.waitForIdle()
    }
    @Test fun emptyInputAndMissingModel() {
        compose.onNodeWithText("Check a message").performScrollTo().performClick()
        compose.onNodeWithText("Check message", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Enter a message to check.").assertExists()
        compose.onNodeWithText("SMS message").performTextInput("Kumusta po, pakiverify ang account. ".repeat(40))
        compose.onNodeWithText("Check message", substring = false).performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Model not installed", substring = false).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("No prediction was made. This prototype needs an evaluated model before it can assess messages.").assertExists()
        compose.onNodeWithText("Review reporting options").performScrollTo().performClick()
        compose.onNodeWithText("Submit report · unavailable").performScrollTo().assertIsNotEnabled()
    }
    @Test fun sharedTextAndClear() {
        val intent = Intent(compose.activity, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND; type = "text/plain"
            putExtra(Intent.EXTRA_TEXT,"Synthetic Taglish share: pakiverify ang bayad")
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        compose.activity.runOnUiThread { compose.activity.startActivity(intent) }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Synthetic Taglish share: pakiverify ang bayad").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Clear message").performScrollTo().performClick()
        compose.onNodeWithText("Synthetic Taglish share: pakiverify ang bayad").assertDoesNotExist()
    }
    @Test fun deniedPermissionsStillAllowManualCheck() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("SMS permission: not granted", substring = true).assertExists()
        compose.onNodeWithText("Check", substring = false).performClick()
        compose.onNodeWithText("SMS message").assertExists()
    }
    @Test fun clipboardPasteWorksWithoutSmsPermission() {
        compose.onNodeWithText("Check a message").performScrollTo().performClick()
        compose.onNodeWithText("SMS message").performClick()
        compose.activityRule.scenario.onActivity {
            it.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                ClipData.newPlainText("Synthetic test", "Synthetic clipboard: kumusta po"))
        }
        compose.onNodeWithText("SMS message").performSemanticsAction(SemanticsActions.PasteText) { it() }
        compose.onNodeWithText("Synthetic clipboard: kumusta po").assertExists()
        // The test owns this synthetic clipboard; application code never clears user clipboard.
        compose.activityRule.scenario.onActivity {
            it.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
