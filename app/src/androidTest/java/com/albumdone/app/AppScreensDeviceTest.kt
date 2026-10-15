package com.albumdone.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.albumdone.app.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses the app like the bride would, on a real Android system, and saves a screenshot of each
 * step (the build workflow sends them to the phone-test-results branch for review).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppScreensDeviceTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun has(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String, timeout: Long = 60_000) {
        compose.waitUntil(timeout) { has(text) }
        compose.waitForIdle()
    }

    private fun waitForTag(tag: String, timeout: Long = 60_000) {
        compose.waitUntil(timeout) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        Thread.sleep(900) // let images and animations settle
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        DeviceTestSupport.saveScreenshot(name, bitmap)
    }

    @Test
    fun everyScreenWorks() {
        // A wedding whose photos are not checked yet: the app checks them just as on a phone.
        TestWedding.create("Priya & Rohan")

        waitFor("Priya & Rohan")
        screenshot("01-home")
        compose.onNodeWithText("Priya & Rohan").performClick()

        // Checking, then the album appears by itself.
        compose.waitUntil(60_000) { has("photos checked") || has("Finding the people") || has("Making your album") }
        screenshot("02-checking")
        waitFor("Send list to photographer", timeout = 8 * 60_000)
        screenshot("03-album")

        // The couple: tap the bride's face.
        compose.onNodeWithTag("couple-bride").performClick()
        waitFor("In ")
        screenshot("04-bride")
        Espresso.pressBack()
        compose.waitForIdle()

        // Add someone the app did not put in the family.
        compose.onNodeWithTag("more-people").performClick()
        waitFor("Other people in your photos")
        screenshot("05-add-someone")
        Espresso.pressBack()
        compose.waitForIdle()

        // Scroll to the photos and open one.
        compose.onNodeWithTag("album-grid").performScrollToNode(hasTestTag("album-photo"))
        screenshot("06-album-photos")
        compose.onAllNodesWithTag("album-photo")[0].performClick()
        waitFor("WHY IT")
        screenshot("07-photo")

        // Swap for a look-alike when there is one.
        if (compose.onAllNodesWithTag("alternative").fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodesWithTag("alternative")[0].performClick()
            waitFor("Swapped")
            screenshot("08-swapped")
        }

        // Remove it: the next best photo takes its place.
        compose.onNodeWithText("Remove").performClick()
        waitFor("Removed")
        screenshot("09-removed")

        // Album size and what was left out.
        compose.onNodeWithContentDescription("Album size and more").performClick()
        waitFor("What we left out")
        screenshot("10-album-size")
        Espresso.pressBack()
        compose.waitForIdle()

        // Send the list.
        compose.onNodeWithText("Send list to photographer").performClick()
        waitFor("Share list")
        screenshot("11-send")
    }
}
