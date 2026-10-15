package com.albumdone.app.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumPlannerTest {

    private var nextId = 1L
    private val minute = 60_000L
    private val start = 1_700_000_000_000L

    /** A clean, sharp, well-exposed photo. Hashes default to far apart so nothing is a burst. */
    private fun photo(
        folder: String = "",
        takenAt: Long = 0,
        sharpness: Double = 100.0,
        brightness: Double = 128.0,
        darkFraction: Double = 0.0,
        brightFraction: Double = 0.0,
        hash: Long? = null,
        faceCount: Int = 1,
        smile: Float = 0.5f,
        eyesClosed: Boolean = false,
        failed: Boolean = false,
        name: String? = null,
    ): PlanPhoto {
        val id = nextId++
        return PlanPhoto(
            id = id,
            name = name ?: "IMG_%04d.jpg".format(id),
            folder = folder,
            takenAt = takenAt,
            sharpness = sharpness,
            brightness = brightness,
            darkFraction = darkFraction,
            brightFraction = brightFraction,
            hash = hash ?: distinctHash(id),
            faceCount = faceCount,
            smile = smile,
            eyesClosed = eyesClosed,
            failed = failed,
        )
    }

    /** Hashes that differ from each other by about 32 bits. */
    private fun distinctHash(id: Long): Long = id * -0x61c8864680b583ebL

    @Test
    fun blurryBelowThirtyPercentOfMedian() {
        val photos = listOf(photo(sharpness = 100.0), photo(sharpness = 100.0), photo(sharpness = 100.0),
            photo(sharpness = 29.0), photo(sharpness = 31.0))
        val a = AlbumPlanner.analyze(photos)
        assertTrue(Issue.BLURRY in a.byId.getValue(photos[3].id).issues)
        assertFalse(Issue.BLURRY in a.byId.getValue(photos[4].id).issues)
        assertEquals(1, a.count(Issue.BLURRY))
    }

    @Test
    fun darkAndBrightRules() {
        val photos = listOf(
            photo(brightness = 39.0),
            photo(brightness = 100.0, darkFraction = 0.66),
            photo(brightness = 221.0),
            photo(brightness = 150.0, brightFraction = 0.51),
            photo(brightness = 41.0, darkFraction = 0.6),
        )
        val a = AlbumPlanner.analyze(photos)
        assertTrue(Issue.TOO_DARK in a.byId.getValue(photos[0].id).issues)
        assertTrue(Issue.TOO_DARK in a.byId.getValue(photos[1].id).issues)
        assertTrue(Issue.TOO_BRIGHT in a.byId.getValue(photos[2].id).issues)
        assertTrue(Issue.TOO_BRIGHT in a.byId.getValue(photos[3].id).issues)
        assertTrue(a.byId.getValue(photos[4].id).usable)
        assertEquals(4, a.darkOrBrightCount)
    }

    @Test
    fun eyesClosedAndUnreadable() {
        val photos = listOf(photo(eyesClosed = true), photo(failed = true), photo())
        val a = AlbumPlanner.analyze(photos)
        assertEquals(setOf(Issue.EYES_CLOSED), a.byId.getValue(photos[0].id).issues)
        assertEquals(setOf(Issue.UNREADABLE), a.byId.getValue(photos[1].id).issues)
        assertEquals(1, a.usableCount)
    }

    @Test
    fun burstKeepsBestShot() {
        val h = 0x0F0F_0F0F_0F0F_0F0FL
        val a1 = photo(takenAt = start, hash = h, smile = 0.2f)
        val a2 = photo(takenAt = start + 1_000, hash = h xor 0b111, smile = 0.9f)
        val a3 = photo(takenAt = start + 3_000, hash = h xor 0b1, smile = 0.4f)
        // 4 seconds after the previous one: not part of the burst.
        val b = photo(takenAt = start + 7_000, hash = h)
        val a = AlbumPlanner.analyze(listOf(a1, a2, a3, b))
        assertTrue(a.byId.getValue(a2.id).usable)
        assertTrue(Issue.DUPLICATE in a.byId.getValue(a1.id).issues)
        assertTrue(Issue.DUPLICATE in a.byId.getValue(a3.id).issues)
        assertTrue(a.byId.getValue(b.id).usable)
        assertEquals(2, a.count(Issue.DUPLICATE))
    }

    @Test
    fun differentPicturesAreNotABurst() {
        val p1 = photo(takenAt = start, hash = 0L)
        val p2 = photo(takenAt = start + 500, hash = 0x1FFFL) // 13 bits apart
        val a = AlbumPlanner.analyze(listOf(p1, p2))
        assertEquals(0, a.count(Issue.DUPLICATE))
    }

    @Test
    fun burstPrefersShotWithoutProblems() {
        val h = 42L
        val blinking = photo(takenAt = start, hash = h, smile = 1f, eyesClosed = true)
        val fine = photo(takenAt = start + 1_000, hash = h, smile = 0.1f)
        val a = AlbumPlanner.analyze(listOf(blinking, fine))
        assertTrue(a.byId.getValue(fine.id).usable)
        assertTrue(Issue.DUPLICATE in a.byId.getValue(blinking.id).issues)
    }

    @Test
    fun burstByFileNameWhenTimeUnknown() {
        val p1 = photo(hash = 7L, name = "DSC_0001.jpg")
        val p2 = photo(hash = 7L, name = "DSC_0002.jpg")
        val p3 = photo(hash = 7L.inv(), name = "DSC_0003.jpg")
        val a = AlbumPlanner.analyze(listOf(p3, p2, p1))
        assertEquals(1, a.count(Issue.DUPLICATE))
        assertTrue(a.byId.getValue(p3.id).usable)
    }

    @Test
    fun burstsDoNotCrossEvents() {
        val p1 = photo(folder = "Haldi", takenAt = start, hash = 5L)
        val p2 = photo(folder = "Wedding", takenAt = start + 1_000, hash = 5L)
        val a = AlbumPlanner.analyze(listOf(p1, p2))
        assertEquals(0, a.count(Issue.DUPLICATE))
    }

    @Test
    fun eventsFromSubFoldersInTimeOrder() {
        val photos = listOf(
            photo(folder = "Wedding Photos/Wedding", takenAt = start + 2 * 86_400_000L),
            photo(folder = "Wedding Photos/Haldi", takenAt = start),
            photo(folder = "Wedding Photos/Reception/Candid", takenAt = start + 3 * 86_400_000L),
            photo(folder = "Wedding Photos/Reception/Traditional", takenAt = start + 3 * 86_400_000L + 5),
        )
        val a = AlbumPlanner.analyze(photos)
        assertEquals(listOf("Haldi", "Wedding", "Reception"), a.events)
        assertEquals("Reception", a.byId.getValue(photos[3].id).event)
        assertEquals(listOf(photos[1].id, photos[0].id, photos[2].id, photos[3].id), a.photos.map { it.id })
    }

    @Test
    fun singleFolderSplitsOnNinetyMinuteGaps() {
        val photos = listOf(
            photo(folder = "All", takenAt = start),
            photo(folder = "All", takenAt = start + 80 * minute),
            photo(folder = "All", takenAt = start + 80 * minute + 91 * minute),
            photo(folder = "All", takenAt = 0),
        )
        val a = AlbumPlanner.analyze(photos)
        assertEquals(3, a.events.size)
        assertTrue(a.events[0].startsWith("Event 1"))
        assertTrue(a.events[1].startsWith("Event 2"))
        assertEquals(AlbumPlanner.OTHER_PHOTOS, a.events[2])
        assertEquals(a.events[0], a.byId.getValue(photos[1].id).event)
        assertEquals(a.events[1], a.byId.getValue(photos[2].id).event)
    }

    @Test
    fun singleFolderWithoutGapsIsOneEvent() {
        val photos = listOf(photo(folder = "Wedding", takenAt = start), photo(folder = "Wedding", takenAt = start + minute))
        assertEquals(listOf("Wedding"), AlbumPlanner.analyze(photos).events)
        val top = listOf(photo(), photo())
        assertEquals(listOf(AlbumPlanner.ALL_PHOTOS), AlbumPlanner.analyze(top).events)
    }

    @Test
    fun allocateIsProportionalWithLargestRemainder() {
        val q = AlbumPlanner.allocate(linkedMapOf("A" to 50, "B" to 30, "C" to 20), 10)
        assertEquals(mapOf("A" to 5, "B" to 3, "C" to 2), q)
        val r = AlbumPlanner.allocate(linkedMapOf("A" to 7, "B" to 7, "C" to 6), 10)
        assertEquals(10, r.values.sum())
        assertEquals(3, r["C"])
    }

    @Test
    fun allocateGivesEveryEventAtLeastOne() {
        val q = AlbumPlanner.allocate(linkedMapOf("A" to 1000, "B" to 2, "C" to 1), 10)
        assertEquals(1, q["B"])
        assertEquals(1, q["C"])
        assertEquals(8, q["A"])
    }

    @Test
    fun allocateWithMoreEventsThanPlaces() {
        val q = AlbumPlanner.allocate(linkedMapOf("A" to 5, "B" to 50, "C" to 10), 2)
        assertEquals(mapOf("B" to 1, "C" to 1), q)
    }

    @Test
    fun allocateWhenFewerPhotosThanPlaces() {
        val q = AlbumPlanner.allocate(linkedMapOf("A" to 3, "B" to 0), 10)
        assertEquals(mapOf("A" to 3), q)
    }

    @Test
    fun shortlistSkipsProblemsAndSharesAcrossEvents() {
        val haldi = (0 until 20).map { photo(folder = "Haldi", takenAt = start + it * 10 * minute) }
        val wedding = (0 until 60).map { photo(folder = "Wedding", takenAt = start + 86_400_000L + it * 10 * minute) }
        val bad = photo(folder = "Wedding", takenAt = start + 86_400_000L + 5, sharpness = 1.0, smile = 1f)
        val a = AlbumPlanner.analyze(haldi + wedding + bad)
        val list = AlbumPlanner.shortlist(a, 20)
        assertEquals(20, list.size)
        assertFalse(bad.id in list)
        assertEquals(5, list.count { a.byId.getValue(it).event == "Haldi" })
        assertEquals(15, list.count { a.byId.getValue(it).event == "Wedding" })
        // Returned in capture order.
        assertEquals(list.sortedBy { a.byId.getValue(it).order }, list)
    }

    @Test
    fun shortlistSpreadsAcrossTimeSlots() {
        // One great moment with many shots, and three quieter moments later.
        val moment = (0 until 10).map { photo(folder = "W", takenAt = start + it * 10_000L, smile = 1f) }
        val others = (1..3).map { photo(folder = "W", takenAt = start + it * 20 * minute, smile = 0.1f) }
        val a = AlbumPlanner.analyze(moment + others)
        val list = AlbumPlanner.shortlist(a, 4)
        assertTrue(others.all { it.id in list })
        assertEquals(1, list.count { id -> moment.any { it.id == id } })
    }

    @Test
    fun shortlistFillsWithBestOfTheRest() {
        val good = photo(folder = "W")
        val blinking = photo(folder = "W", eyesClosed = true)
        val darkAndBlinking = photo(folder = "W", eyesClosed = true, brightness = 10.0)
        val broken = photo(folder = "W", failed = true)
        val a = AlbumPlanner.analyze(listOf(good, blinking, darkAndBlinking, broken))
        assertEquals(setOf(good.id, blinking.id), AlbumPlanner.shortlist(a, 2).toSet())
        assertEquals(3, AlbumPlanner.shortlist(a, 10).size)
    }

    @Test
    fun removedPhotosAreNeverPickedAndReplaceRefills() {
        val photos = (0 until 10).map { photo(folder = "W", takenAt = start + it * 10 * minute, smile = it / 10f) }
        val a = AlbumPlanner.analyze(photos)
        val first = AlbumPlanner.shortlist(a, 5)
        val gone = first.first()
        val kept = (first - gone).toSet()

        val refilled = AlbumPlanner.shortlist(a, 5, removed = setOf(gone), keep = kept)
        assertEquals(5, refilled.size)
        assertFalse(gone in refilled)
        assertTrue(refilled.containsAll(kept))

        // The replacement is the best photo that was not already on the list.
        val expected = a.photos.filter { it.id !in first }.maxBy { it.score }.id
        assertTrue(expected in refilled)

        // Even a fresh shortlist never brings it back.
        assertFalse(gone in AlbumPlanner.shortlist(a, 10, removed = setOf(gone)))
    }

    @Test
    fun scorePrefersSharpSmilingFaces() {
        val sharpSmile = photo(sharpness = 200.0, smile = 0.9f)
        val softNoFace = photo(sharpness = 50.0, smile = -1f, faceCount = 0)
        assertTrue(AlbumPlanner.score(sharpSmile, 100.0) > AlbumPlanner.score(softNoFace, 100.0))
        // Sharpness is capped at twice the median.
        assertEquals(
            AlbumPlanner.score(photo(sharpness = 200.0), 100.0),
            AlbumPlanner.score(photo(sharpness = 5000.0), 100.0),
            1e-9,
        )
    }

    @Test
    fun emptyInputs() {
        val a = AlbumPlanner.analyze(emptyList())
        assertTrue(a.events.isEmpty())
        assertTrue(AlbumPlanner.shortlist(a, 10).isEmpty())
        assertTrue(AlbumPlanner.shortlist(AlbumPlanner.analyze(listOf(photo())), 0).isEmpty())
    }
}
