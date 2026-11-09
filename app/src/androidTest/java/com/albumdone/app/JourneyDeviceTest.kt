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
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.albumdone.app.data.AppDatabase
import com.albumdone.app.data.PhotoEntity
import com.albumdone.app.data.ProjectEntity
import com.albumdone.app.plan.AlbumSize
import com.albumdone.app.plan.Occasion
import com.albumdone.app.ui.AlbumViewModel
import com.albumdone.app.ui.MainActivity
import java.util.regex.Pattern
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    private val args get() = InstrumentationRegistry.getArguments()

    /** The folder in Pictures the bride picks (the real-phone run can name a real event's folder). */
    private val folderName get() = args.getString("journeyFolder") ?: "Sharma Wedding"

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
        val tests = InstrumentationRegistry.getInstrumentation().context.assets
        val real = DeviceTestSupport.testAssets("wedding")
        // (asset path, event folder, file name)
        val photos = if (real.isNotEmpty()) {
            real.filter { DeviceTestSupport.testAssets("wedding/$it").isNotEmpty() }.sorted()
                .flatMap { f -> DeviceTestSupport.testAssets("wedding/$f").sorted().map { Triple("wedding/$f/$it", f, it) } }
                .take(InstrumentationRegistry.getArguments().getString("journeyPhotos")?.toIntOrNull() ?: MAX_PHOTOS)
        } else {
            TestWedding.PHOTOS.map { (asset, folder, name) -> Triple("faces/$asset", folder, name) }
        }
        for ((asset, folder, name) in photos) {
            tests.open(asset).use { putPhoto(folder, name, it) }
        }
        return photos.size
    }

    // ---- The showcase recording (showcase=1): the app used slowly, like a person, for the owner ----

    private val showcase get() = args.getString("showcase") == "1"
    private val testStart = android.os.SystemClock.elapsedRealtime()
    private val marks = StringBuilder()

    /**
     * A caption for the recording from this moment on (seconds since the test started). The
     * real-phone run turns these into the video's captions and speeds up only "Checking".
     */
    private fun mark(caption: String) {
        marks.append("%.1f\t%s\n".format((android.os.SystemClock.elapsedRealtime() - testStart) / 1000.0, caption))
        runCatching { DeviceTestSupport.screensDir.apply { mkdirs() }.resolve("showcase-marks.txt").writeText(marks.toString()) }
        Log.i("AlbumDoneTest", "Showcase: $caption")
    }

    /** Waits like a person looking at the screen; the screen keeps moving (the test drives its clock). */
    private fun look(ms: Long) {
        if (!showcase) return
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(100)
        }
    }

    /** Taps like a person: a short look first, then the tap, then a look at what happened. */
    private fun personTap(before: Long = 1_200, after: Long = 2_000, tap: () -> Unit) {
        look(before); tap(); compose.waitForIdle(); look(after)
    }

    /** Each photo put in Pictures: (its address, event folder, file name). */
    private val placed = mutableListOf<Triple<String, String, String>>()

    /**
     * The folder picker fault: since a 2025 security fix, Android's storage service checks every
     * folder's path up to /storage/emulated, and listing /storage or /storage/emulated from the shell
     * leaves it unable to see that folder for some minutes, so it hides everything ("No items").
     * Found on the emulator on 1 Nov 2026 (tools/phone/picker_check.sh): untouched it lists folders,
     * right after `ls -la /storage/emulated` it does not. Test Lab works on the phone's storage from
     * the shell before each test. A bride never does; this test must not either. If the picker still
     * shows no folders after waiting, the same photos are given to the app directly, as the
     * real-photo test does, and checking is measured the same way.
     */
    private var labPickerFault = false

    /** Runs a shell command (no shell operators) and returns what it printed. */
    private fun shell(command: String): String = runCatching {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            .use { pfd -> java.io.FileInputStream(pfd.fileDescriptor).use { String(it.readBytes()) } }
    }.getOrElse { "failed: $it" }

    /**
     * What the phone's storage looks like, for the picker fault: Android's storage service walks up
     * from each folder to /storage/emulated and hides everything when it cannot see that folder.
     */
    private fun storageNotes(): String = buildString {
        for (c in listOf(
            "getprop ro.build.version.security_patch", "getprop ro.build.fingerprint", "id",
            "am get-current-user", "pm list users",
            // Only stat, never list: listing /storage or /storage/emulated brings the fault on.
            "ls -ld /storage /storage/emulated /storage/emulated/0 /storage/emulated/0/Android/data /sdcard",
            "sm list-volumes all",
        )) append("$ ").append(c).append('\n').append(shell(c)).append('\n')
    }

    private fun putPhoto(folder: String, name: String, input: java.io.InputStream) {
        val resolver = DeviceTestSupport.app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$folderName/$folder")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { out -> input.copyTo(out) }
        placed += Triple(uri.toString(), folder, name)
    }

    /**
     * Thousands of photos pushed by the real-phone run as one ZIP (Test Lab cannot push 2,500
     * separate files: it gave up after 12 minutes, three times), unpacked into Pictures here.
     * Entries are "<event folder>/<file name>". The app cannot read another app's file in
     * Download, so the shell reads it (path without spaces).
     */
    private fun unzipPhotos(path: String): Int {
        var n = 0
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        val pipe = shell.executeShellCommand("cat $path")
        val input = android.os.ParcelFileDescriptor.AutoCloseInputStream(pipe)
        java.util.zip.ZipInputStream(input.buffered(1 shl 20)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !entry.name.endsWith(".jpg", ignoreCase = true)) continue
                val name = entry.name.removePrefix("./")
                putPhoto(name.substringBeforeLast('/', ""), name.substringAfterLast('/'), zip)
                n++
            }
        }
        shell.executeShellCommand("rm $path").close()
        return n
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

    /** The app's text is on screen (false while another app's screen, such as a share sheet, is in front). */
    private fun onScreen(text: String) = runCatching { has(text) }.getOrDefault(false)

    /** Presses Back until the app is in front again. */
    private fun backToApp() {
        val app = DeviceTestSupport.app.packageName
        repeat(4) {
            if (device.currentPackageName == app) return
            DeviceTestSupport.pressBack()
            device.wait(Until.hasObject(By.pkg(app).depth(0)), 3_000)
        }
        assertTrue("the app came back after the share sheet", device.currentPackageName == app)
    }

    /** The app's memory in use (PSS), in MB, from Android's own count. */
    private fun memoryMb(): Int = runCatching {
        val out = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys meminfo ${DeviceTestSupport.app.packageName}")
            .use { pfd -> java.io.FileInputStream(pfd.fileDescriptor).use { String(it.readBytes()) } }
        val kb = Regex("TOTAL PSS:\\s*(\\d+)").find(out)?.groupValues?.get(1)
            ?: Regex("(?m)^\\s*TOTAL\\s+(\\d+)").find(out)?.groupValues?.get(1)
        (kb!!.toLong() / 1024).toInt()
    }.getOrDefault(-1)

    /** Goes to the phone's own storage in the picker; false if it shows no Pictures folder. */
    private fun openPhoneStorage(): Boolean {
        if (find("^Pictures$", timeout = 4_000) != null) return true
        val roots = device.findObject(By.desc(Pattern.compile("(?i)show roots|navigate up|open navigation drawer")))
        if (roots != null) {
            roots.click()
            Thread.sleep(1000)
            // The phone itself, by the names pickers use for it.
            tapText("^${Pattern.quote(Build.MODEL)}$", "^Internal storage$", "^Galaxy .*", "^Pixel .*", "^This device$", "^Phone$", "^AOSP.*", "^sdk_gphone.*")
        }
        // A Pixel opens at the phone's storage with no menu button. Its folder list can be empty for
        // some minutes if the lab listed the storage from the shell (see labPickerFault): wait,
        // scroll, pull to reload.
        val end = System.currentTimeMillis() + 4 * 60_000
        while (System.currentTimeMillis() < end) {
            runCatching { UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView("Pictures") }
            if (find("^Pictures$", timeout = 10_000) != null) return true
            device.swipe(device.displayWidth / 2, device.displayHeight / 3, device.displayWidth / 2, device.displayHeight * 2 / 3, 30)
        }
        shot("02b-picker-no-pictures")
        return false
    }

    @Test
    fun fromFolderToSentList() {
        assumeTrue("The folder picker needs Android 10 or later", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        // A bigger folder than an album takes today, to measure how far the app can go.
        args.getString("journeyMax")?.toIntOrNull()?.let { AlbumSize.MAX_PHOTOS = it }
        // Camera-size photos are pushed into Pictures by the real-phone run before the test starts.
        val pushed = args.getString("journeyPushed") == "1"
        val zip = args.getString("journeyZip")
        val count = step("00-photos") {
            when {
                zip != null -> unzipPhotos(zip)
                pushed -> args.getString("journeyCount")!!.toInt()
                else -> putPhotosOnThePhone()
            }
        }
        Log.i("AlbumDoneTest", "Journey: $count photos in Pictures/$folderName")
        Log.i("AlbumDoneTest", "Journey: Android ${Build.VERSION.RELEASE}, security update ${Build.VERSION.SECURITY_PATCH}")

        mark("Home screen")
        step("01-home") {
            compose.waitUntil(30_000) { has("Choose photos folder") || has("New album") }
            // The intro fades by the screen's own clock, which the test drives.
            compose.mainClock.advanceTimeBy(5_000)
            appShot("01-home")
        }

        step("02-picker") {
            mark("Choosing the photos folder (Android's own folder picker)")
            val button = if (has("Choose photos folder")) "Choose photos folder" else "New album"
            compose.onNodeWithText(button).performClick()
            device.wait(Until.hasObject(By.pkg(Pattern.compile(".*documentsui.*"))), 15_000)
            Thread.sleep(1500)
            shot("02-picker")
            if (!openPhoneStorage()) {
                labPickerFault = true
                Log.w("AlbumDoneTest", "Journey: the folder picker shows no folders (lab phone fault); photos given to the app directly")
                DeviceTestSupport.screensDir.apply { mkdirs() }.resolve("journey-lab-storage.txt").writeText(storageNotes())
                backToApp()
                return@step
            }
            tapText("^Pictures$")
            tapText("^${Pattern.quote(folderName)}$")
            shot("03-picker-folder")
            tapText("^Use this folder$", "^Select$", "^Allow access.*")
            // Android asks once more whether the app may read the folder.
            shot("04-picker-allow")
            tapText("^Allow$", timeout = 10_000)
            mark("The app reads the folder: photos and the photographer's event folders")
        }

        if (!labPickerFault) step("05-new-album") {
            // The app reads the folder and guesses the occasion from its name.
            compose.waitUntil(60_000) { has("What are these photos of?") }
            mark("New album: the app counts the photos and guesses the occasion")
            look(5_000)
            appShot("05-new-album")
            assertTrue("folder count shown", has("%,d photos".format(count)))
            val vm = ViewModelProvider(compose.activity)[AlbumViewModel::class.java]
            if (folderName.contains("wedding", ignoreCase = true)) {
                assertEquals("a wedding folder is guessed as a wedding", Occasion.WEDDING, vm.newOccasion)
            }
        }

        var checkingStarted = 0L
        step("06-start") {
            if (labPickerFault) {
                assertTrue("photos to give the app directly", placed.isNotEmpty())
                val dao = AppDatabase.get(DeviceTestSupport.app).dao()
                val albumSize = args.getString("albumSize")?.toIntOrNull()?.takeIf { it > 0 } ?: 300
                val id = runBlocking {
                    val id = dao.insertProject(ProjectEntity(name = folderName, treeUri = "file:///lab-picker-fault",
                        createdAt = System.currentTimeMillis(), albumSize = albumSize))
                    dao.insertPhotos(placed.map { (uri, folder, name) ->
                        PhotoEntity(projectId = id, docUri = uri, name = name, folder = folder, size = 1, lastModified = 0)
                    })
                    id
                }
                val vm = ViewModelProvider(compose.activity)[AlbumViewModel::class.java]
                compose.runOnUiThread { vm.openProject(id) }
                mark("Checking every photo on the phone (no internet)")
                checkingStarted = System.currentTimeMillis()
                compose.waitUntil(30_000) { has("Making your album") }
                compose.mainClock.advanceTimeBy(20_000)
                appShot("06-progress")
                return@step
            }
            look(1_500)
            mark("Checking every photo on the phone (no internet)")
            compose.onNodeWithText("Start checking photos").performClick()
            checkingStarted = System.currentTimeMillis()
            // Android asks whether the app may show notifications (Android 13+), unless allowed
            // already: say yes. A tap while the dialog is still sliding in is ignored by Android, so
            // keep tapping Allow until the app's progress screen is back.
            val end = System.currentTimeMillis() + 30_000
            while (!onScreen("Making your album")) {
                assertTrue("checking did not start", System.currentTimeMillis() < end)
                device.findObject(text("^Allow$"))?.click()
                Thread.sleep(1_000)
            }
            compose.mainClock.advanceTimeBy(20_000)
            appShot("06-progress")
        }

        step("07-album") {
            // About 1.3 s a photo on a Galaxy A15; the emulator is slower.
            val end = System.currentTimeMillis() + count * 15_000L + 5 * 60_000
            // Every minute: photos checked, the step, and the app's memory, to see whether a big
            // folder slows the phone down or fills its memory as it goes.
            val dao = AppDatabase.get(DeviceTestSupport.app).dao()
            val projectId = runBlocking { dao.projectSummaries().first().filter { it.project.name == folderName }.maxOf { it.project.id } }
            val scaling = StringBuilder("seconds\tchecked\tstep\tmemory MB\n")
            var lastSample = 0L
            var lastStatus = ""
            // The test drives the screen's clock, so the screen only moves on when the test looks:
            // look often, so the recording shows the album as soon as the phone has it.
            while (!has("Send photos to photographer")) {
                assertTrue("the album was not ready in time", System.currentTimeMillis() < end)
                val now = System.currentTimeMillis()
                val status = runBlocking { dao.project(projectId)?.status }.orEmpty()
                if (now - lastSample >= 60_000 || status != lastStatus) {
                    lastSample = now
                    lastStatus = status
                    scaling.append("${(now - checkingStarted) / 1000}\t${runBlocking { dao.countProcessed(projectId) }}\t$status\t${memoryMb()}\n")
                    DeviceTestSupport.screensDir.apply { mkdirs() }.resolve("journey-scaling.txt").writeText(scaling.toString())
                }
                compose.mainClock.advanceTimeBy(2_000)
                Thread.sleep(2_000)
            }
            mark("The album is ready")
            scaling.append("${(System.currentTimeMillis() - checkingStarted) / 1000}\t$count\tALBUM SHOWN\t${memoryMb()}\n")
            DeviceTestSupport.screensDir.resolve("journey-scaling.txt").writeText(scaling.toString())
            val seconds = (System.currentTimeMillis() - checkingStarted) / 1000
            Log.i("AlbumDoneTest", "Journey: $count photos checked and the album made in $seconds s " +
                "(${"%.2f".format(seconds.toDouble() / count)} s a photo) on ${Build.MANUFACTURER} ${Build.MODEL}")
            DeviceTestSupport.screensDir.apply { mkdirs() }.resolve("journey-speed.txt")
                .writeText("$count photos in Pictures/$folderName, checked and the album made in $seconds s on ${Build.MANUFACTURER} ${Build.MODEL}" +
                    (if (labPickerFault) " (folder picker skipped: this lab phone's picker shows no folders)" else "") + "\n")
            appShot("07-album")
            // The phone's own album, as the brain chose it (before this test swaps and removes),
            // for studying against a couple's real album.
            val album = runBlocking { dao.photos(projectId) }.filter { it.selected }
                .joinToString("") { (if (it.folder.isEmpty()) it.name else "${it.folder}/${it.name}") + "\n" }
            DeviceTestSupport.screensDir.resolve("journey-album.txt").writeText(album)
            // Who the app took for the couple and why, and whether the brain or the rules made the album.
            runCatching {
                val project = runBlocking { dao.project(projectId) }!!
                val (planPhotos, tags) = runBlocking { com.albumdone.app.people.PeopleRepository(dao).planInput(project) }
                com.albumdone.app.analysis.MindLoader.ensure(DeviceTestSupport.app)
                val analysis = com.albumdone.app.plan.AlbumPlanner.analyze(planPhotos, tags)
                val by = if (com.albumdone.app.plan.AlbumMind.weights != null && com.albumdone.app.plan.AlbumMind.canSee(analysis)) "the brain (AlbumMind)" else "the rules"
                DeviceTestSupport.screensDir.resolve("journey-couple.txt").writeText(
                    "album made by $by\n" + com.albumdone.app.people.PeoplePlan.explainCouple(project.brideId, project.groomId, planPhotos))
            }.onFailure { Log.e("AlbumDoneTest", "couple report", it) }
            compose.onNodeWithTag("album-grid").performScrollToNode(hasTestTag("album-photo"))
            appShot("08-album-photos")
        }

        if (showcase) step("showcase") { explore() }

        if (!showcase) step("09-photo") {
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
            // Remove (and Undo) can already have closed the photo; Back only if it is still open.
            if (has("WHY IT")) DeviceTestSupport.pressBack()
            waitFor("Send photos to photographer")
        }

        step("13-send") {
            mark("Sending the chosen photos to the photographer")
            look(1_500)
            compose.onNodeWithText("Send photos to photographer").performSemanticsAction(SemanticsActions.OnClick)
            waitFor("Save photos to a folder")
            look(4_000)
            appShot("13-send")
        }

        if (!labPickerFault) step("13b-save-photos") {
            // The photographer gets the chosen photos as a folder: the bride picks where (Pictures).
            compose.onNodeWithTag("save-photos").performClick()
            device.wait(Until.hasObject(By.pkg(Pattern.compile(".*documentsui.*"))), 15_000)
            Thread.sleep(1500)
            // The picker opens in Pictures, where the wedding folder is; if not, go there.
            if (find("^${Pattern.quote(folderName)}$", timeout = 3_000) == null) {
                check(openPhoneStorage()) { "The folder picker shows no Pictures folder" }
                tapText("^Pictures$")
            }
            shot("13b-save-picker")
            tapText("^Use this folder$", "^Select$", "^Allow access.*")
            tapText("^Allow$", timeout = 10_000)
            compose.waitUntil(count * 2_000L + 60_000) { onScreen("photos saved") }
            appShot("13c-saved")
        }

        step("14-share-list") {
            compose.onNodeWithTag("share-list").performClick()
            // Android's own share sheet: any app the bride would send the list with.
            Thread.sleep(2500)
            shot("14-share-sheet")
            // Back to the app: a phone's share sheet can show a tip that takes the first Back.
            backToApp()
        }

        step("15-home") {
            mark("Back home: the album card")
            repeat(3) { if (!onScreen("Your albums")) DeviceTestSupport.pressBack() }
            compose.waitUntil(15_000) { onScreen("Your albums") }
            appShot("15-home")
            assertTrue("the album card is on the home screen", has(folderName))
            look(4_000)
            mark("end")
        }
    }

    private fun scrollGrid(up: Boolean) = compose.onNodeWithTag("album-grid").performTouchInput {
        if (up) swipeUp(durationMillis = 900) else swipeDown(durationMillis = 900)
    }

    /** Back to the top of the album (the cover, the couple and the album size button live there). */
    private fun albumTop() {
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("album-grid").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag("album-grid").performScrollToIndex(0)
        look(1_500)
    }

    /**
     * One part of the showcase. A part the folder does not offer is skipped; a part that goes wrong is
     * written down (log, screenshot, what was on screen) and the recording goes on with the next part.
     */
    private fun part(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            Log.e("AlbumDoneTest", "Showcase part $name went wrong", e)
            runCatching { shot("s-$name-FAILED") }
            val notes = StringBuilder(e.stackTraceToString()).append("\n\n")
            runCatching { notes.append(compose.onAllNodes(isRoot()).printToString(maxDepth = Int.MAX_VALUE)) }
            runCatching { DeviceTestSupport.screensDir.apply { mkdirs() }.resolve("showcase-$name-FAILED.txt").writeText(notes.toString()) }
            mark("(skipped: $name)")
            // Back to the album for the next part.
            repeat(3) { if (!onScreen("Send photos to photographer")) { DeviceTestSupport.pressBack(); look(1_500) } }
        }
    }

    /**
     * The album looked at the way the bride would, slowly: the cover and the couple, the photos, why
     * they were picked, a swap, a removal and Undo, the album size and what was left out, her own
     * photos, the family, and naming people in Who's who (last: it remakes the album).
     */
    private fun explore() {
        mark("The album: cover photo and the couple, found on their own")
        look(6_000)
        appShot("s01-album-top")
        if (hasTag("confirm-groom")) part("confirm-groom") {
            mark("The app is not sure about the groom and asks once: Yes")
            personTap(2_500, 3_000) { compose.onNodeWithTag("confirm-groom").performClick() }
        }

        part("scroll") {
            mark("Looking through the album")
            repeat(6) { scrollGrid(up = true); look(2_500) }
            appShot("s02-album-scrolled")
            albumTop()
        }

        part("photo") {
            mark("A photo: why it is in the album")
            compose.onNodeWithTag("album-grid").performScrollToNode(hasTestTag("album-photo"))
            look(1_500)
            personTap { compose.onAllNodesWithTag("album-photo")[0].performSemanticsAction(SemanticsActions.OnClick) }
            waitFor("WHY IT")
            look(5_000)
            appShot("s08-photo")
            repeat(3) {
                mark("The next photo, and why")
                runCatching { compose.onAllNodes(isRoot())[0].performTouchInput { swipeLeft(durationMillis = 500) } }
                look(4_500)
            }
            if (hasTag("alternative")) {
                mark("Look-alike photos: swap one in with one tap")
                look(2_000)
                personTap(1_500, 3_500) { compose.onAllNodesWithTag("alternative")[0].performClick() }
                appShot("s09-swapped")
            }
            mark("Remove: the next best photo takes its place")
            personTap(1_500, 3_000) { compose.onNodeWithText("Remove").performClick() }
            appShot("s10-removed")
            // Undo shows only for a few seconds: tap it at once, like a person who changed their mind.
            if (has("Undo")) runCatching {
                mark("Undo")
                compose.onNodeWithText("Undo").performClick()
                look(3_000)
            }
            if (has("WHY IT")) { DeviceTestSupport.pressBack(); look(1_500) }
            waitFor("Send photos to photographer")
        }

        part("album-size") {
            albumTop()
            mark("Album size and what we left out")
            personTap { compose.onNodeWithContentDescription("Album size and more").performClick() }
            waitFor("What we left out")
            look(5_000)
            appShot("s11-album-size")
            DeviceTestSupport.pressBack()
            compose.waitUntil(10_000) { !has("What we left out") }
            look(1_500)
            compose.onNodeWithTag("album-grid").performScrollToNode(hasTestTag("left-out"))
            personTap { compose.onNodeWithTag("left-out").performSemanticsAction(SemanticsActions.OnClick) }
            waitFor("What we left out")
            look(4_000)
            mark("A photo we left out, and why")
            personTap { compose.onAllNodesWithTag("left-out-photo")[0].performSemanticsAction(SemanticsActions.OnClick) }
            look(5_000)
            appShot("s12-left-out-photo")
            DeviceTestSupport.pressBack(); look(1_000)
            DeviceTestSupport.pressBack()
            waitFor("Send photos to photographer")
            look(2_000)
        }

        part("bride") {
            albumTop()
            mark("One of the couple: their own photos")
            personTap { compose.onNodeWithTag("couple-bride").performClick() }
            look(4_000)
            appShot("s03-bride")
            DeviceTestSupport.pressBack(); look(1_500)
        }

        part("people") {
            mark("Family & friends: everyone in the photos, most photographed first")
            compose.onNodeWithTag("album-grid").performScrollToNode(hasTestTag("see-all-people"))
            personTap { compose.onNodeWithTag("see-all-people").performClick() }
            waitFor("Family & friends")
            look(4_000)
            appShot("s04-people")
            for ((tag, caption) in listOf("filter-BRIDE" to "Bride's side", "filter-GROOM" to "Groom's side", "filter-unnamed" to "Not named yet", "filter-all" to "Everyone")) {
                if (!hasTag(tag)) continue
                mark("Family & friends: $caption")
                personTap(1_000, 3_000) { compose.onNodeWithTag(tag).performClick() }
            }
            if (compose.onAllNodesWithTag("person-tile").fetchSemanticsNodes().size > 3) {
                mark("One relative's photos")
                personTap { compose.onAllNodesWithTag("person-tile")[3].performClick() }
                look(4_000)
                appShot("s05-person")
                DeviceTestSupport.pressBack(); look(1_500)
            }
            DeviceTestSupport.pressBack()
            waitFor("Send photos to photographer")
            look(1_500)
        }

        if (hasTag("quick-tag")) part("whos-who") {
            mark("Who's who: how close is each person? One tap each (test answers)")
            personTap { compose.onNodeWithTag("quick-tag").performClick() }
            compose.waitUntil(30_000) { hasTag("quick-must") || has("All done") }
            // Answers a person might give; the test cannot know this family, so they are examples.
            val answers = listOf("must", "must", "close", "must", "close", "skip", "close", "must", "not", "close")
            for ((i, a) in answers.withIndex()) {
                if (!hasTag("quick-must")) break
                look(2_500)
                if (i == 0) appShot("s06-whos-who")
                when (a) {
                    "skip" -> personTap(800, 1_500) { compose.onNodeWithTag("quick-skip").performClick() }
                    "not" -> personTap(800, 1_500) { compose.onNodeWithTag("quick-not-family").performClick() }
                    else -> personTap(1_000, 1_500) { compose.onNodeWithTag("quick-$a").performClick() }
                }
            }
            // Leave the rest for later: Back, or the "All done" card's button.
            if (!has("All done")) { DeviceTestSupport.pressBack(); look(1_500) }
            if (has("See your album")) {
                look(2_500)
                appShot("s07-whos-who-done")
                personTap { compose.onNodeWithText("See your album").performClick() }
            }
            waitFor("Send photos to photographer", timeout = 120_000)
            mark("The album, remade with the family named")
            albumTop()
            look(4_000)
            appShot("s13-album-after-naming")
        }
    }

    companion object {
        /**
         * Enough for a real day's feel, small enough for every run. The real-phone run can ask for
         * more (instrumentation argument journeyPhotos, e.g. 1000 for the full-size folder).
         */
        const val MAX_PHOTOS = 120
    }
}
