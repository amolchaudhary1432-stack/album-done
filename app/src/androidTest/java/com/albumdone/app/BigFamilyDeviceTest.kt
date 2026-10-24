package com.albumdone.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.albumdone.app.data.AppDatabase
import com.albumdone.app.data.PersonEntity
import com.albumdone.app.data.ProjectEntity
import com.albumdone.app.data.ProjectStatus
import com.albumdone.app.people.FaceCrops
import com.albumdone.app.people.PeopleRepository
import com.albumdone.app.plan.Priority
import com.albumdone.app.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A big Indian wedding, made up: one folder of 600 photos with the couple, 40 relatives who are
 * with them at several times of the day, and 20 guests who go up on the stage once. The photos
 * are drawn (coloured faces with a number), so this checks the family guess and the screens at
 * the scale of a real family, not the photo checks. Screenshots go to phone-test-results.
 */
@RunWith(AndroidJUnit4::class)
class BigFamilyDeviceTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun has(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
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

    @Test
    fun fortyRelativesAreEasyToHandle() {
        val name = "Big family"
        val seeded = runBlocking { seed(name) }

        // The family guess, before any screen: the relatives in, the stage guests out.
        val dao = AppDatabase.get(DeviceTestSupport.app).dao()
        val tags = runBlocking { PeopleRepository(dao).planInput(dao.project(seeded.projectId)!!).second }
        fun guessedFamily(id: Long) = tags.tag(id).priority.let { it == Priority.MUST || it == Priority.IMPORTANT }
        val relativesFound = seeded.relatives.count { guessedFamily(it) }
        val guestsFound = seeded.guests.count { guessedFamily(it) }
        android.util.Log.i("AlbumDoneTest", "Big family: $relativesFound of 40 relatives guessed, $guestsFound of 20 guests")
        assertTrue("relatives guessed: $relativesFound of 40", relativesFound >= 36)
        assertEquals("stage guests guessed as family", 0, guestsFound)

        compose.waitUntil(30_000) { has(name) }
        screenshot("big-01-home")
        compose.onNodeWithText(name).performClick()
        compose.waitUntil(3 * 60_000) { has("Send photos to photographer") }
        screenshot("big-02-album")
        compose.onNodeWithTag("album-grid").performScrollToNode(hasTestTag("quick-tag"))
        screenshot("big-03-family")

        // Everyone, then one side only.
        tap("see-all-people")
        compose.waitUntil(30_000) { has("Family & friends") }
        screenshot("big-04-people")
        tap("filter-BRIDE")
        screenshot("big-05-people-bride-side")
        tap("filter-all")
        compose.onNodeWithTag("people-grid").performTouchInput { swipeUp() }
        screenshot("big-06-people-more")
        DeviceTestSupport.pressBack()
        compose.waitUntil(30_000) { has("Send photos to photographer") }

        // Who's who: a few faces, one tap each, then back to the album.
        compose.onNodeWithTag("album-grid").performScrollToIndex(2)
        compose.waitForIdle()
        tap("quick-tag")
        compose.waitUntil(30_000) { hasTag("quick-Aunt") }
        screenshot("big-07-whos-who")
        tap("quick-Mother")
        screenshot("big-08-whos-who-second")
        tap("quick-Father")
        tap("quick-skip")
        tap("quick-not-family")
        tap("quick-Aunt")
        screenshot("big-09-whos-who-sixth")
        DeviceTestSupport.pressBack()
        compose.waitUntil(2 * 60_000) { has("Send photos to photographer") }
        compose.waitUntil(30_000) { has("Album updated") }
        screenshot("big-10-album-after")
        val named = runBlocking { dao.people(seeded.projectId).count { it.name.isNotBlank() } }
        assertEquals("bride, groom and the three people named", 5, named)

        // Family groups: one photo per group, name a whole family at once, add every family.
        // The card at the top of the screen, clear of the floating Send button (hero 0, couple 1, family 2, groups 3).
        compose.onNodeWithTag("album-grid").performScrollToIndex(3)
        compose.waitForIdle()
        tap("stage-card")
        compose.waitUntil(30_000) { hasTag("stage-tile") }
        screenshot("big-11-family-groups")
        tap("name-group")
        compose.waitUntil(10_000) { hasTag("save-group") }
        screenshot("big-12-name-group")
        tap("group-side-GROOM")
        tap("save-group")
        compose.waitUntil(2 * 60_000) { has("Album updated") }
        screenshot("big-13-group-named")
        // Every family in the album already: nothing to add, so the button is not shown.
        if (hasTag("add-all-groups")) {
            tap("add-all-groups")
            compose.waitUntil(60_000) { has("family photo") }
        }
        screenshot("big-14-every-family-added")
        DeviceTestSupport.pressBack()
        compose.waitUntil(30_000) { has("Send photos to photographer") }

        // One family's own copy of the album.
        compose.onNodeWithText("Send photos to photographer").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(30_000) { has("Save photos to a folder") }
        tap("album-for-BRIDE")
        compose.waitUntil(60_000) { has("Bride's family album") }
        screenshot("big-15-bride-family-album")
    }

    private class Seeded(val projectId: Long, val relatives: List<Long>, val guests: List<Long>)

    /** Makes the wedding as if it had been checked by the app already. */
    private suspend fun seed(name: String): Seeded {
        val app = DeviceTestSupport.app
        val dao = AppDatabase.get(app).dao()
        val pid = dao.insertProject(
            ProjectEntity(name = name, treeUri = "file:///big", createdAt = System.currentTimeMillis(), albumSize = 90, status = ProjectStatus.DONE),
        )
        val bride = dao.insertPerson(PersonEntity(projectId = pid, name = "Priya"))
        val groom = dao.insertPerson(PersonEntity(projectId = pid, name = "Rohan"))
        val relatives = List(40) { dao.insertPerson(PersonEntity(projectId = pid)) }
        val guests = List(20) { dao.insertPerson(PersonEntity(projectId = pid)) }
        val labels = HashMap<Long, String>().apply {
            put(bride, "B"); put(groom, "G")
            relatives.forEachIndexed { i, id -> put(id, "R${i + 1}") }
            guests.forEachIndexed { i, id -> put(id, "g${i + 1}") }
        }
        // (people, minute of the day): the couple all day, each relative at four times (once
        // getting ready with the bride or the groom), families in groups, guests on stage once.
        val shots = ArrayList<Pair<List<Long>, Int>>()
        repeat(200) { k -> shots += listOf(bride, groom) to k * 2 }
        relatives.forEachIndexed { i, r ->
            val ownSide = if (i < 20) bride else groom
            shots += listOf(ownSide, r) to 10 + i
            shots += listOf(ownSide, r) to 10 + i
            for (visit in 1..3) repeat(2) { shots += listOf(bride, groom, r) to visit * 110 + i }
        }
        relatives.chunked(5).forEachIndexed { g, family -> repeat(2) { shots += (listOf(bride, groom) + family) to 60 + g * 3 } }
        guests.forEachIndexed { j, g -> repeat(3) { shots += listOf(bride, groom, g) to 420 + j } }
        repeat(600 - shots.size) { k -> shots += emptyList<Long>() to k * 20 + 7 }
        shots.sortBy { it.second }

        DrawnPhotos.store(dao, pid, shots, labels)
        dao.setCouple(pid, bride, groom)
        dao.setCoupleSure(pid, true)
        FaceCrops.saveAll(app, dao, pid)
        return Seeded(pid, relatives, guests)
    }
}
