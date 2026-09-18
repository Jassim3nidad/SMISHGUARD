package ph.smishguard

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Test-only screenshots of static UI; no real message input is ever entered here. */
class VisualReviewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun captureStaticScreens() {
        val app = compose.activity.application as SmishGuardApp
        runBlocking { app.preferences.clear() }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        try {
            capture("onboarding")
            compose.onNodeWithText("Continue without permissions").performScrollTo().performClick()
            compose.waitForIdle()
            capture("home")
            compose.onNodeWithText("Check a message").performScrollTo().performClick()
            capture("check")
            compose.onNodeWithText("Settings").performClick()
            capture("settings")
        } finally {
            compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(compose.activity.filesDir,"visual-review").apply { mkdirs() }
        File(directory,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
}
