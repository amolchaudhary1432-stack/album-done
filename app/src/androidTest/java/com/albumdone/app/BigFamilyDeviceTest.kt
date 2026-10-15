package com.albumdone.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.albumdone.app.data.AppDatabase
import com.albumdone.app.data.FaceEntity
import com.albumdone.app.data.PersonEntity
import com.albumdone.app.data.PhotoEntity
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
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

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
        compose.waitUntil(3 * 60_000) { has("Send list to photographer") }
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
        compose.waitUntil(30_000) { has("Send list to photographer") }

        // Who's who: a few faces, one tap each, then back to the album.
        compose.onNodeWithTag("album-grid").performScrollToNode(hasTestTag("quick-tag"))
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
        compose.waitUntil(2 * 60_000) { has("Send list to photographer") }
        compose.waitUntil(30_000) { has("Album updated") }
        screenshot("big-10-album-after")
        val named = runBlocking { dao.people(seeded.projectId).count { it.name.isNotBlank() } }
        assertEquals("bride, groom and the three people named", 5, named)

        // Family groups: one photo per group, name a whole family at once, add every family.
        compose.onNodeWithTag("album-grid").performScrollToNode(hasTestTag("stage-card"))
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
        tap("add-all-groups")
        compose.waitUntil(60_000) { has("family photo") }
        screenshot("big-14-every-family-added")
        DeviceTestSupport.pressBack()
        compose.waitUntil(30_000) { has("Send list to photographer") }
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
        val hues = HashMap<Long, Float>().apply { labels.keys.forEachIndexed { i, id -> put(id, (i * 137.5f) % 360f) } }

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

        val dir = File(app.filesDir, "thumbs/$pid").apply { mkdirs() }
        val start = 1_700_000_000_000L
        val random = Random(7)
        val photos = shots.mapIndexed { n, (people, minute) ->
            val file = File(dir, "IMG_%04d.jpg".format(n + 1))
            draw(people, labels, hues, minute, file)
            PhotoEntity(
                projectId = pid, docUri = Uri.fromFile(file).toString(), name = file.name, folder = "", size = file.length(),
                lastModified = 0, processed = true, takenAt = start + minute * 60_000L + n * 4_000L, width = 1200, height = 800,
                sharpness = 300.0, peakSharpness = 600.0, brightness = 125.0, darkFraction = 0.03, brightFraction = 0.01,
                hash = random.nextLong(), faceCount = people.size, peopleArea = (0.1f * people.size).coerceAtMost(0.8f),
                thumbPath = file.path,
            )
        }
        dao.insertPhotos(photos)
        val stored = dao.photos(pid).associateBy { it.name }
        val faces = shots.flatMapIndexed { n, (people, _) ->
            val photoId = stored.getValue("IMG_%04d.jpg".format(n + 1)).id
            people.mapIndexed { k, person ->
                val box = faceBox(k, people.size)
                FaceEntity(
                    projectId = pid, photoId = photoId, left = box[0], top = box[1], right = box[2], bottom = box[3],
                    eyes = "OPEN", expression = "SMILE", smile = 0.8f, looking = true, directionX = 0f, yaw = 0f,
                    sharpness = 400.0, brightness = 130.0, backlit = false, occlusion = 0f, cutOff = 0f, quality = 0.8f, personId = person,
                )
            }
        }
        faces.chunked(500).forEach { dao.insertFaces(it) }
        for ((id, count) in dao.personPhotoCounts(pid).map { it.personId to it.photos }) dao.setPersonStats(id, count, dao.bestFaceOf(id))
        dao.setCouple(pid, bride, groom)
        dao.setCoupleSure(pid, true)
        FaceCrops.saveAll(app, dao, pid)
        return Seeded(pid, relatives, guests)
    }

    /** Faces side by side across the middle of the photo. */
    private fun faceBox(k: Int, n: Int): FloatArray {
        val w = if (n <= 3) 0.16f else 0.11f
        val gap = (1f - n * w) / (n + 1)
        val left = gap + k * (w + gap)
        return floatArrayOf(left, 0.28f, left + w, 0.28f + w * 1.5f)
    }

    private fun draw(people: List<Long>, labels: Map<Long, String>, hues: Map<Long, Float>, minute: Int, out: File) {
        val w = 1200; val h = 800
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.HSVToColor(floatArrayOf((minute * 0.6f) % 360f, 0.25f, 0.85f)))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        if (people.isEmpty()) {
            paint.color = Color.HSVToColor(floatArrayOf(40f, 0.6f, 0.7f))
            c.drawRect(w * 0.3f, h * 0.3f, w * 0.7f, h * 0.7f, paint) // decor
        }
        people.forEachIndexed { k, person ->
            val b = faceBox(k, people.size)
            val cx = (b[0] + b[2]) / 2f * w; val cy = (b[1] + b[3]) / 2f * h
            val r = (b[2] - b[0]) * w / 2f
            paint.color = Color.HSVToColor(floatArrayOf(hues.getValue(person), 0.55f, 0.75f))
            c.drawCircle(cx, cy, r, paint)
            c.drawRect(cx - r * 1.2f, cy + r * 1.1f, cx + r * 1.2f, h.toFloat(), paint) // shoulders
            text.textSize = r * 0.8f
            c.drawText(labels.getValue(person), cx, cy + r * 0.28f, text)
        }
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()
    }
}
