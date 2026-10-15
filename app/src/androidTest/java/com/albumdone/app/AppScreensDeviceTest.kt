package com.albumdone.app

import android.util.Log
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.albumdone.app.ui.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Uses the app like the bride would, on a real Android system, and saves a screenshot of each
 * step (the build workflow sends them to the phone-test-results branch for review). A step that
 * fails saves a screenshot and a text dump of the screen, and the walk-through carries on.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppScreensDeviceTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val failures = mutableListOf<String>()

    private fun has(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun hasTag(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String, timeout: Long = 30_000) {
        compose.waitUntil(timeout) { has(text) }
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        Thread.sleep(900) // let images and animations settle
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        DeviceTestSupport.saveScreenshot(name, bitmap)
    }

    /** Runs one step; on failure keeps a screenshot and what was on screen, then goes back once. */
    private fun step(name: String, goBackOnFailure: Boolean = true, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            Log.e("AlbumDoneTest", "Step $name failed", e)
            failures += "$name: ${e.message?.lineSequence()?.firstOrNull()}"
            runCatching { screenshot("$name-FAILED") }
            runCatching {
                val dir = File(DeviceTestSupport.app.filesDir, "screens").apply { mkdirs() }
                File(dir, "$name-FAILED-screen.txt").writeText(compose.onAllNodes(isRoot()).printToString())
            }
            if (goBackOnFailure) runCatching { Espresso.pressBack(); compose.waitForIdle() }
        }
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

        step("04-bride") {
            compose.onNodeWithTag("couple-bride").performClick()
            waitFor("In ")
            screenshot("04-bride")
            Espresso.pressBack()
            compose.waitForIdle()
        }

        step("05-add-someone") {
            compose.onNodeWithTag("more-people").performClick()
            waitFor("Other people in your photos")
            screenshot("05-add-someone")
            Espresso.pressBack()
            compose.waitForIdle()
        }

        step("06-album-photos", goBackOnFailure = false) {
            compose.onNodeWithTag("album-grid").performScrollToNode(hasTestTag("album-photo"))
            screenshot("06-album-photos")
        }

        step("07-photo") {
            compose.onAllNodesWithTag("album-photo")[0].performClick()
            waitFor("WHY IT")
            screenshot("07-photo")

            // Swap for a look-alike when there is one.
            if (hasTag("alternative")) {
                compose.onAllNodesWithTag("alternative")[0].performClick()
                waitFor("Swapped")
                screenshot("08-swapped")
            }

            // Remove it: the next best photo takes its place.
            compose.onNodeWithText("Remove").performClick()
            waitFor("Removed")
            screenshot("09-removed")
        }

        step("10-album-size") {
            compose.onNodeWithContentDescription("Album size and more").performClick()
            waitFor("What we left out")
            screenshot("10-album-size")
            Espresso.pressBack()
            compose.waitForIdle()
        }

        step("11-send") {
            compose.onNodeWithText("Send list to photographer").performClick()
            waitFor("Share list")
            screenshot("11-send")
        }

        assertTrue("Steps that failed:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
