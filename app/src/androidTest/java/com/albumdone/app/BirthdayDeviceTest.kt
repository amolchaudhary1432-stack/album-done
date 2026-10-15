package com.albumdone.app

import android.net.Uri
import android.util.Log
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.albumdone.app.data.AppDatabase
import com.albumdone.app.data.PersonEntity
import com.albumdone.app.data.ProjectEntity
import com.albumdone.app.data.ProjectStatus
import com.albumdone.app.people.FaceCrops
import com.albumdone.app.plan.Occasion
import com.albumdone.app.scan.FoundPhoto
import com.albumdone.app.ui.AlbumViewModel
import com.albumdone.app.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Album Done is not only for weddings: a child's birthday folder, made up and drawn like the big
 * family test. Checks that the occasion is guessed from the folder name, that the album is built
 * around the birthday star with mummy and papa, and that no screen talks about a bride, a groom
 * or their sides.
 */
@RunWith(AndroidJUnit4::class)
class BirthdayDeviceTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val failures = mutableListOf<String>()

    private fun has(text: String) = compose.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
    private fun hasTag(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun screenshot(name: String) {
        compose.waitForIdle()
        Thread.sleep(900)
        DeviceTestSupport.saveScreenshot(name, InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
    }

    private fun tap(tag: String) {
        compose.waitUntil(20_000) { hasTag(tag) }
        compose.onAllNodesWithTag(tag)[0].performClick()
        compose.waitForIdle()
    }

    /** No wedding words on this screen. */
    private fun noWeddingWords(where: String) {
        for (word in listOf("bride", "groom", "couple")) assertTrue("\"$word\" on $where", !has(word))
    }

    private fun step(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            Log.e("AlbumDoneTest", "Step $name failed", e)
            failures += "$name: ${e.message?.lineSequence()?.firstOrNull()}"
            runCatching { screenshot("$name-FAILED") }
        }
    }

    private fun backToAlbum() {
        repeat(3) {
            if (has("Send list to photographer")) return
            DeviceTestSupport.pressBack()
            compose.waitForIdle()
        }
        compose.waitUntil(30_000) { has("Send list to photographer") }
    }

    @Test
    fun aBirthdayIsNotAWedding() {
        // A new album from a folder called "Aarav 1st Birthday": the occasion is guessed from the name.
        step("bday-01-new-album") {
            val vm = ViewModelProvider(compose.activity)[AlbumViewModel::class.java]
            val photos = List(240) { FoundPhoto(Uri.parse("file:///test/IMG_%04d.jpg".format(it + 1)), "IMG_%04d.jpg".format(it + 1), "", 1, 0) }
            compose.runOnUiThread { vm.showScanned(Uri.parse("file:///test"), "Aarav 1st Birthday", photos) }
            compose.waitUntil(10_000) { hasTag("occasion-sub-KIDS_BIRTHDAY") }
            assertEquals(Occasion.KIDS_BIRTHDAY, vm.newOccasion)
            screenshot("bday-01-new-album")
            compose.runOnUiThread { vm.back() }
        }

        val name = "Aarav turns one"
        runBlocking { seed(name) }
        compose.waitUntil(30_000) { has(name) }
        compose.onNodeWithText(name).performClick()
        compose.waitUntil(3 * 60_000) { has("Send list to photographer") }

        step("bday-02-album") {
            screenshot("bday-02-album")
            assertTrue("main person card", has("Birthday star"))
            assertTrue("mummy and papa guessed", has("mummy & papa"))
            noWeddingWords("the album")
        }
        step("bday-02b-parent") {
            tap("parent-1")
            compose.waitUntil(30_000) { has("In ") }
            Thread.sleep(1_500)
            screenshot("bday-02b-parent")
            DeviceTestSupport.pressBack()
        }
        backToAlbum()
        step("bday-03-family") {
            compose.onNodeWithTag("album-grid").performScrollToNode(hasTestTag("quick-tag"))
            screenshot("bday-03-family")
            noWeddingWords("the family strip")
        }
        step("bday-04-people") {
            compose.onNodeWithTag("album-grid").performScrollToIndex(2)
            tap("see-all-people")
            compose.waitUntil(30_000) { has("Family & friends") }
            screenshot("bday-04-people")
            assertTrue("side filters shown", !hasTag("filter-BRIDE"))
            tap("person-tile")
            compose.waitUntil(30_000) { has("In ") }
            Thread.sleep(1_500)
            screenshot("bday-05-person")
            noWeddingWords("the person sheet")
        }
        backToAlbum()
        step("bday-06-whos-who") {
            compose.onNodeWithTag("album-grid").performScrollToIndex(2)
            tap("quick-tag")
            compose.waitUntil(30_000) { hasTag("quick-Aunt") }
            screenshot("bday-06-whos-who")
            assertTrue("whose side asked", !has("Whose side"))
            tap("quick-Grandmother")
        }
        backToAlbum()
        step("bday-07-groups") {
            compose.onNodeWithTag("album-grid").performScrollToIndex(3)
            tap("stage-card")
            compose.waitUntil(30_000) { hasTag("stage-tile") }
            screenshot("bday-07-groups")
            noWeddingWords("family groups")
            tap("name-group")
            compose.waitUntil(10_000) { hasTag("save-group") }
            screenshot("bday-08-name-group")
            noWeddingWords("naming a group")
            tap("group-side-FAMILY")
            tap("save-group")
            compose.waitUntil(2 * 60_000) { has("Album updated") }
        }
        backToAlbum()
        step("bday-09-photo") {
            compose.onNodeWithTag("album-grid").performScrollToNode(hasTestTag("album-photo"))
            compose.onAllNodesWithTag("album-photo")[0].performClick()
            compose.waitUntil(30_000) { has("WHY IT") }
            screenshot("bday-09-photo")
            noWeddingWords("the photo view")
        }
        backToAlbum()
        step("bday-10-send") {
            compose.onNodeWithText("Send list to photographer").performClick()
            compose.waitUntil(30_000) { has("Share list") }
            screenshot("bday-10-send")
            assertTrue("whose album shown", !hasTag("album-for-BRIDE"))
        }
        assertTrue("Steps that failed:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** A first birthday as if the app had checked it: the star all day, family at several times, friends once. */
    private suspend fun seed(name: String) {
        val app = DeviceTestSupport.app
        val dao = AppDatabase.get(app).dao()
        val pid = dao.insertProject(
            ProjectEntity(name = name, treeUri = "file:///bday", createdAt = System.currentTimeMillis(), albumSize = 40,
                status = ProjectStatus.DONE, occasion = Occasion.KIDS_BIRTHDAY.name),
        )
        val star = dao.insertPerson(PersonEntity(projectId = pid))
        val mummy = dao.insertPerson(PersonEntity(projectId = pid))
        val papa = dao.insertPerson(PersonEntity(projectId = pid))
        val family = List(10) { dao.insertPerson(PersonEntity(projectId = pid)) }
        val friends = List(12) { dao.insertPerson(PersonEntity(projectId = pid)) }
        val labels = HashMap<Long, String>().apply {
            put(star, "A"); put(mummy, "M"); put(papa, "P")
            family.forEachIndexed { i, id -> put(id, "F${i + 1}") }
            friends.forEachIndexed { i, id -> put(id, "f${i + 1}") }
        }
        val shots = ArrayList<Pair<List<Long>, Int>>()
        repeat(70) { k -> shots += listOf(star) to k * 4 }
        // Mummy and papa all day: with the child, and the three of them together.
        repeat(30) { k -> shots += listOf(star, if (k % 2 == 0) mummy else papa) to k * 9 + 1 }
        repeat(8) { k -> shots += listOf(mummy, star, papa) to k * 35 + 3 }
        family.forEachIndexed { i, r -> for (visit in 0..3) repeat(2) { shots += listOf(star, r) to visit * 70 + i * 3 + 1 } }
        family.chunked(4).forEachIndexed { g, group -> repeat(2) { shots += (listOf(star) + group) to 150 + g * 5 } }
        friends.forEachIndexed { j, f -> repeat(2) { shots += listOf(star, f) to 230 + j } }
        repeat(240 - shots.size) { k -> shots += emptyList<Long>() to k * 9 + 2 }
        shots.sortBy { it.second }
        DrawnPhotos.store(dao, pid, shots, labels)
        dao.setCouple(pid, star, null)
        dao.setCoupleSure(pid, true)
        FaceCrops.saveAll(app, dao, pid)
    }
}
