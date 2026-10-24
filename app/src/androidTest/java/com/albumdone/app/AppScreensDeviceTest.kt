package com.albumdone.app

import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
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

    private fun back() {
        DeviceTestSupport.pressBack()
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        Thread.sleep(900) // let images and animations settle
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        DeviceTestSupport.saveScreenshot(name, bitmap)
    }

    /** Runs one step; on failure keeps a screenshot, what was on screen and why, then goes back once. */
    private fun step(name: String, goBackOnFailure: Boolean = true, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            Log.e("AlbumDoneTest", "Step $name failed", e)
            failures += "$name: ${e.message?.lineSequence()?.firstOrNull()}"
            val dir = DeviceTestSupport.screensDir.apply { mkdirs() }
            val notes = StringBuilder(e.stackTraceToString()).append("\n\n")
            // Errors inside the app's screens are held back by the test tools until the next sync.
            runCatching { compose.waitForIdle() }.exceptionOrNull()?.let {
                Log.e("AlbumDoneTest", "Error inside the app during $name", it)
                notes.append("ERROR INSIDE THE APP:\n").append(it.stackTraceToString()).append("\n\n")
            }
            runCatching { notes.append(compose.onAllNodes(isRoot()).printToString(maxDepth = Int.MAX_VALUE)) }
                .onFailure { notes.append("Could not read the screen: $it") }
            File(dir, "$name-FAILED-screen.txt").writeText(notes.toString())
            runCatching { screenshot("$name-FAILED") }
            if (goBackOnFailure) runCatching { back() }
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
        var opened = false
        step("03-album", goBackOnFailure = false) {
            waitFor("Send photos to photographer", timeout = 8 * 60_000)
            screenshot("03-album")
            opened = true
        }
        assertTrue("The album did not open:\n" + failures.joinToString("\n"), opened)

        // When the app is not sure who the groom is, it asks once.
        if (hasTag("confirm-groom")) {
            step("03b-choose-groom") {
                compose.onNodeWithTag("choose-groom").performClick()
                waitFor("Who is the groom?")
                screenshot("03b-choose-groom")
                back()
                compose.onNodeWithTag("confirm-groom").performClick()
                compose.waitUntil(10_000) { !hasTag("confirm-groom") }
                screenshot("03c-groom-confirmed")
            }
        }

        step("04-bride") {
            compose.onNodeWithTag("couple-bride").performClick()
            waitFor("In ")
            screenshot("04-bride")
            back()
        }

        step("05-people") {
            compose.onNodeWithTag("album-grid").performScrollToNode(hasTestTag("see-all-people"))
            compose.onNodeWithTag("see-all-people").performClick()
            waitFor("Family & friends")
            screenshot("05-people")
            val tiles = compose.onAllNodesWithTag("person-tile").fetchSemanticsNodes().size
            if (tiles > 2) {
                // Someone besides the bride and groom.
                compose.onAllNodesWithTag("person-tile")[2].performClick()
                waitFor("In ")
                screenshot("05b-person")
                back()
            }
            back()
            waitFor("Send photos to photographer")
        }

        // "Who's who": one face at a time, one tap each.
        if (hasTag("quick-tag")) {
            step("05c-whos-who") {
                compose.onNodeWithTag("quick-tag").performClick()
                compose.waitUntil(30_000) { hasTag("quick-Aunt") }
                screenshot("05c-whos-who")
                compose.onNodeWithTag("quick-Aunt").performClick()
                compose.waitForIdle()
                screenshot("05d-whos-who-next")
                back()
                waitFor("Send photos to photographer", timeout = 60_000)
                screenshot("05e-after-whos-who")
            }
        }

        step("06-album-photos", goBackOnFailure = false) {
            compose.onNodeWithTag("album-grid").performScrollToNode(hasTestTag("album-photo"))
            screenshot("06-album-photos")
        }

        step("07-photo") {
            compose.onAllNodesWithTag("album-photo")[0].performSemanticsAction(SemanticsActions.OnClick)
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
            back()
            // The sheet slides away before the album can be tapped again.
            compose.waitUntil(10_000) { !has("What we left out") }
        }

        step("10b-left-out") {
            compose.onNodeWithTag("album-grid").performScrollToNode(hasTestTag("left-out"))
            compose.onNodeWithTag("left-out").performSemanticsAction(SemanticsActions.OnClick)
            waitFor("What we left out")
            screenshot("10b-left-out")
            compose.onAllNodesWithTag("left-out-photo")[0].performSemanticsAction(SemanticsActions.OnClick)
            waitFor("Add to album")
            screenshot("10c-left-out-photo")
            compose.onNodeWithTag("add-to-album").performSemanticsAction(SemanticsActions.OnClick)
            waitFor("Added to your album")
            back()
            back()
            waitFor("Send photos to photographer")
        }

        step("11-send") {
            compose.onNodeWithText("Send photos to photographer").performSemanticsAction(SemanticsActions.OnClick)
            waitFor("Save photos to a folder")
            screenshot("11-send")
        }

        assertTrue("Steps that failed:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
