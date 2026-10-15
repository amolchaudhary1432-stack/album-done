package com.albumdone.app

import android.util.Log
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.albumdone.app.data.AppDatabase
import com.albumdone.app.data.PhotoEntity
import com.albumdone.app.data.ProjectEntity
import com.albumdone.app.people.PeopleRepository
import com.albumdone.app.plan.AlbumPlanner
import com.albumdone.app.ui.MainActivity
import com.albumdone.app.work.AnalyzeWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A real wedding: freely licensed photos (from the test-photos branch, copied into the test
 * package by the workflow), checked by the real worker, then the album the app makes is
 * reported photo by photo and screenshotted while scrolling, so Claude can judge the picks
 * like the couple would. Skipped when the build has no such photos.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RealWeddingDeviceTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun has(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun screenshot(name: String) {
        compose.waitForIdle()
        Thread.sleep(1200)
        DeviceTestSupport.saveScreenshot(name, InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
    }

    @Test
    fun realWedding() {
        val folders = DeviceTestSupport.testAssets("wedding").filter { DeviceTestSupport.testAssets("wedding/$it").isNotEmpty() }
        assumeTrue("No real wedding photos in this build", folders.isNotEmpty())
        val name = "Real wedding"
        val dao = AppDatabase.get(DeviceTestSupport.app).dao()

        val files = folders.sorted().flatMap { f -> DeviceTestSupport.testAssets("wedding/$f").sorted().map { f to it } }
        val asked = InstrumentationRegistry.getArguments().getString("albumSize")?.toIntOrNull() ?: 0
        val albumSize = if (asked > 0) asked else (files.size / 4).coerceIn(12, 60)
        val id = runBlocking {
            val id = dao.insertProject(ProjectEntity(name = name, treeUri = "file:///real", createdAt = System.currentTimeMillis(), albumSize = albumSize))
            dao.insertPhotos(files.map { (folder, file) ->
                PhotoEntity(
                    projectId = id,
                    docUri = DeviceTestSupport.copyAsset("wedding/$folder/$file", "real-wedding/$folder", file).toString(),
                    name = file, folder = folder, size = 1, lastModified = 0,
                )
            })
            id
        }

        val started = System.currentTimeMillis()
        val result = runBlocking {
            TestListenableWorkerBuilder<AnalyzeWorker>(DeviceTestSupport.app)
                .setInputData(workDataOf(AnalyzeWorker.KEY_PROJECT_ID to id))
                .build()
                .doWork()
        }
        val seconds = (System.currentTimeMillis() - started) / 1000
        assertTrue("analysis finished: $result", result is ListenableWorker.Result.Success)

        val project = runBlocking { dao.project(id)!! }
        val (planPhotos, tags) = runBlocking { PeopleRepository(dao).planInput(project) }
        val analysis = AlbumPlanner.analyze(planPhotos, tags)
        val list = AlbumPlanner.shortlist(analysis, albumSize)
        val failed = runBlocking { dao.photos(id).count { it.failed } }
        DeviceTestSupport.writeReport(
            analysis, "real-wedding-report.txt", list,
            header = "${files.size} photos in ${folders.size} folders, checked in $seconds s " +
                "(${"%.1f".format(seconds.toDouble() / files.size)} s each on the emulator), $failed unreadable, album size $albumSize",
        )
        // Everything the planner saw, so the album can be re-planned and scored on a computer.
        java.io.File(DeviceTestSupport.app.filesDir, "screens").apply { mkdirs() }
            .resolve("plan-input.txt").writeText(com.albumdone.app.plan.PlanDump.write(planPhotos, tags))
        Log.i("AlbumDoneTest", "Real wedding: ${files.size} photos in $seconds s")

        // Open it in the app, as the couple would after the notification.
        compose.waitUntil(30_000) { has(name) }
        compose.onNodeWithText(name).performClick()
        compose.waitUntil(5 * 60_000) { has("Send list to photographer") }
        screenshot("real-01-album")
        for (i in 2..14) {
            compose.onNodeWithTag("album-grid").performTouchInput { swipeUp() }
            screenshot("real-%02d-scroll".format(i))
        }

        // The first family face, if the app found family.
        compose.onNodeWithTag("album-grid").performTouchInput { repeat(20) { swipeDown() } }
        compose.waitForIdle()
        if (compose.onAllNodesWithTag("person-chip").fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodesWithTag("person-chip")[0].performClick()
            compose.waitUntil(30_000) { has("In ") }
            screenshot("real-20-family-person")
            DeviceTestSupport.pressBack()
            compose.waitForIdle()
        }
        // Everyone the app found, and the one-tap "Who's who".
        if (compose.onAllNodesWithTag("see-all-people").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("see-all-people").performClick()
            compose.waitUntil(30_000) { has("Family & friends") }
            screenshot("real-22-people")
            compose.onNodeWithTag("people-grid").performTouchInput { swipeUp() }
            screenshot("real-23-people-more")
            DeviceTestSupport.pressBack()
            compose.waitForIdle()
        }
        if (compose.onAllNodesWithTag("quick-tag").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("quick-tag").performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("quick-Aunt").fetchSemanticsNodes().isNotEmpty() }
            screenshot("real-24-whos-who")
            compose.onNodeWithTag("quick-skip").performClick()
            compose.waitForIdle()
            screenshot("real-25-whos-who-next")
            DeviceTestSupport.pressBack()
            compose.waitUntil(60_000) { has("Send list to photographer") }
        }
        if (compose.onAllNodesWithTag("cover").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("cover").performClick()
            compose.waitUntil(30_000) { has("WHY IT") }
            screenshot("real-21-cover")
        }
    }
}
