package com.albumdone.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.albumdone.app.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks through every screen like a user, on a real Android system, and saves a screenshot of
 * each one (collected by the build workflow).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppScreensDeviceTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String, timeout: Long = 60_000) {
        compose.waitUntil(timeout) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        Thread.sleep(800) // let images and animations settle
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        DeviceTestSupport.saveScreenshot(name, bitmap)
    }

    @Test
    fun everyScreenWorks() {
        TestWedding.createAndAnalyse("Screens test")

        // Home: the album is listed.
        waitFor("Screens test")
        screenshot("01-home")
        compose.onNodeWithText("Screens test").performClick()

        // Tag your family: the couple was found.
        waitFor("The couple")
        screenshot("02-tag-family")
        compose.onNodeWithText("Done").performClick()

        // Results: counts, then choose the album size.
        waitFor("photos checked")
        screenshot("03-results")
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("4")
        compose.onNodeWithText("Make my shortlist").performScrollTo().performClick()

        // Shortlist: 4 photos picked, each says why.
        waitFor("4 of 4 photos picked")
        screenshot("04-shortlist")
        compose.onAllNodesWithTag("shortlist-photo")[0].performClick()
        waitFor("Take out")
        screenshot("05-why-this-photo")
        compose.onNodeWithText("Take out").performClick()

        // Replace removed brings the album back to 4.
        waitFor("Replace removed")
        screenshot("06-after-removing")
        compose.onNodeWithText("Replace removed", substring = true).performClick()
        waitFor("4 of 4 photos picked")
        screenshot("07-replaced")
    }
}
