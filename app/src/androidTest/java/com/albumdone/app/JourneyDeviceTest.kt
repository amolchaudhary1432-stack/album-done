package com.albumdone.app

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.albumdone.app.plan.Occasion
import com.albumdone.app.ui.AlbumViewModel
import com.albumdone.app.ui.MainActivity
import java.util.regex.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole app from start to finish, the way the bride uses it: the photos sit in a folder on the
 * phone, she taps "Choose photos folder", picks the folder in Android's own folder picker, answers
 * "What are these photos of?", picks the album size, waits while the phone checks the photos, then
 * looks through the album, swaps and removes a photo and sends the list. Nothing is set up behind
 * the app's back. Uses the real-wedding photos when the build has them (the real-phone run), else
 * the small test wedding. A screenshot of every step, including the system's folder picker.
 * UiAutomator is used only for the system's own screens (folder picker, permission, share sheet);
 * the app's screens are driven by the Compose test tools, as in the other tests.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class JourneyDeviceTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val folderName = "Sharma Wedding"

    private fun has(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun hasTag(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String, timeout: Long = 30_000) {
        compose.waitUntil(timeout) { has(text) }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        Thread.sleep(900)
        DeviceTestSupport.saveScreenshot("journey-$name", InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
    }

    /** A screenshot of the app's own screen, once it has settled. */
    private fun appShot(name: String) {
        compose.waitForIdle()
        shot(name)
    }

    /** On failure: the screenshot and what was on screen, then the test stops. */
    private fun <T> step(name: String, block: () -> T): T = try {
        block()
    } catch (e: Throwable) {
        Log.e("AlbumDoneTest", "Journey step $name failed", e)
        runCatching { shot("$name-FAILED") }
        val notes = StringBuilder(e.stackTraceToString()).append("\n\n")
        runCatching { notes.append(compose.onAllNodes(isRoot()).printToString(maxDepth = Int.MAX_VALUE)) }
        runCatching {
            val dump = java.io.ByteArrayOutputStream()
            device.dumpWindowHierarchy(dump)
            notes.append("\n\nWHOLE SCREEN (system too):\n").append(dump.toString())
        }
        DeviceTestSupport.screensDir.apply { mkdirs() }.resolve("journey-$name-FAILED-screen.txt").writeText(notes.toString())
        throw e
    }

    /** The test photos, put in Pictures/Sharma Wedding/<event folder>/ like a copied pendrive. */
    private fun putPhotosOnThePhone(): Int {
        val resolver = DeviceTestSupport.app.contentResolver
        val tests = InstrumentationRegistry.getInstrumentation().context.assets
        val real = DeviceTestSupport.testAssets("wedding")
        // (asset path, event folder, file name)
        val photos = if (real.isNotEmpty()) {
            real.filter { DeviceTestSupport.testAssets("wedding/$it").isNotEmpty() }.sorted()
                .flatMap { f -> DeviceTestSupport.testAssets("wedding/$f").sorted().map { Triple("wedding/$f/$it", f, it) } }
                .take(MAX_PHOTOS)
        } else {
            TestWedding.PHOTOS.map { (asset, folder, name) -> Triple("faces/$asset", folder, name) }
        }
        for ((asset, folder, name) in photos) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$folderName/$folder")
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            resolver.openOutputStream(uri)!!.use { out -> tests.open(asset).use { it.copyTo(out) } }
        }
        return photos.size
    }

    // ---- Android's folder picker (another app, so driven by what is on the screen) ----

    private fun text(regex: String) = By.text(Pattern.compile(regex, Pattern.CASE_INSENSITIVE))

    private fun find(vararg regex: String, timeout: Long = 5_000): UiObject2? {
        val end = System.currentTimeMillis() + timeout
        do {
            for (r in regex) device.findObject(text(r))?.let { return it }
            Thread.sleep(250)
        } while (System.currentTimeMillis() < end)
        return null
    }

    private fun tapText(vararg regex: String, timeout: Long = 10_000) {
        val o = find(*regex, timeout = timeout) ?: error("Nothing on screen says ${regex.joinToString(" / ")}")
        o.click()
        device.waitForIdle()
        Thread.sleep(700)
    }

    /** Goes to the phone's own storage in the picker, unless it is already showing Pictures. */
    private fun openPhoneStorage() {
        if (find("^Pictures$", timeout = 4_000) != null) return
        val roots = device.findObject(By.desc(Pattern.compile("(?i)show roots|navigate up|open navigation drawer")))
            ?: error("The folder picker has no menu button")
        roots.click()
        Thread.sleep(1000)
        // The phone itself, by the names pickers use for it.
        tapText("^${Pattern.quote(Build.MODEL)}$", "^Internal storage$", "^Galaxy .*", "^Pixel .*", "^This device$", "^Phone$", "^AOSP.*", "^sdk_gphone.*")
    }

    @Test
    fun fromFolderToSentList() {
        assumeTrue("The folder picker needs Android 10 or later", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val count = step("00-photos") { putPhotosOnThePhone() }
        Log.i("AlbumDoneTest", "Journey: $count photos in Pictures/$folderName")

        step("01-home") {
            compose.waitUntil(30_000) { has("Choose photos folder") || has("New album") }
            // The intro fades by the screen's own clock, which the test drives.
            compose.mainClock.advanceTimeBy(5_000)
            appShot("01-home")
        }

        step("02-picker") {
            val button = if (has("Choose photos folder")) "Choose photos folder" else "New album"
            compose.onNodeWithText(button).performClick()
            device.wait(Until.hasObject(By.pkg(Pattern.compile(".*documentsui.*"))), 15_000)
            Thread.sleep(1500)
            shot("02-picker")
            openPhoneStorage()
            tapText("^Pictures$")
            tapText("^${Pattern.quote(folderName)}$")
            shot("03-picker-folder")
            tapText("^Use this folder$", "^Select$", "^Allow access.*")
            // Android asks once more whether the app may read the folder.
            shot("04-picker-allow")
            tapText("^Allow$", timeout = 10_000)
        }

        step("05-new-album") {
            // The app reads the folder and guesses the occasion from its name.
            compose.waitUntil(60_000) { has("What are these photos of?") }
            appShot("05-new-album")
            assertTrue("folder count shown", has("%,d photos".format(count)))
            val vm = ViewModelProvider(compose.activity)[AlbumViewModel::class.java]
            assertEquals("a wedding folder is guessed as a wedding", Occasion.WEDDING, vm.newOccasion)
        }

        step("06-start") {
            compose.onNodeWithText("Start checking photos").performClick()
            // Notifications were usually allowed when the app was installed; if Android asks, say yes.
            if (find("^Allow$", timeout = 3_000) != null) tapText("^Allow$")
            compose.waitUntil(30_000) { has("Making your album") }
            compose.mainClock.advanceTimeBy(20_000)
            appShot("06-progress")
        }

        step("07-album") {
            // About 1.3 s a photo on a Galaxy A15; the emulator is slower.
            val end = System.currentTimeMillis() + count * 15_000L + 5 * 60_000
            while (!has("Send list to photographer")) {
                assertTrue("the album was not ready in time", System.currentTimeMillis() < end)
                compose.mainClock.advanceTimeBy(10_000)
                Thread.sleep(10_000)
            }
            appShot("07-album")
            compose.onNodeWithTag("album-grid").performScrollToNode(hasTestTag("album-photo"))
            appShot("08-album-photos")
        }

        step("09-photo") {
            compose.onAllNodesWithTag("album-photo")[0].performSemanticsAction(SemanticsActions.OnClick)
            waitFor("WHY IT")
            appShot("09-photo")
            if (hasTag("alternative")) {
                compose.onAllNodesWithTag("alternative")[0].performClick()
                waitFor("Swapped")
                appShot("10-swapped")
            }
            compose.onNodeWithText("Remove").performClick()
            waitFor("Removed")
            appShot("11-removed")
            if (has("Undo")) {
                compose.onNodeWithText("Undo").performClick()
                compose.waitForIdle()
                appShot("12-undo")
            }
            DeviceTestSupport.pressBack()
            waitFor("Send list to photographer")
        }

        step("13-send") {
            compose.onNodeWithText("Send list to photographer").performSemanticsAction(SemanticsActions.OnClick)
            waitFor("Share list")
            appShot("13-send")
            compose.onNodeWithText("Share list").performClick()
            // Android's own share sheet: any app the bride would send the list with.
            Thread.sleep(2500)
            shot("14-share-sheet")
            DeviceTestSupport.pressBack()
        }

        step("15-home") {
            repeat(3) { if (!has("Your albums")) DeviceTestSupport.pressBack() }
            compose.waitUntil(15_000) { has("Your albums") }
            appShot("15-home")
            assertTrue("the album card is on the home screen", has(folderName))
        }
    }

    companion object {
        /** Enough for a real day's feel and the time it takes, small enough for every run. */
        const val MAX_PHOTOS = 120
    }
}
