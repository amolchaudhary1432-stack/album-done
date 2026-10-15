package com.albumdone.app.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StageGroupsTest {

    private var nextId = 1L
    private val start = 1_700_000_000_000L
    private val second = 1_000L
    private val BRIDE = 1L
    private val GROOM = 2L

    private fun face(person: Long, x: Float, eyes: EyeState = EyeState.OPEN) = PlanFace(
        personId = person, box = Box(x, 0.3f, x + 0.1f, 0.45f), eyes = eyes, expression = Expression.SMILE,
        smile = 0.7f, lookingAtCamera = true, directionX = 0f, sharpness = 100.0, brightness = 130.0,
    )

    private fun shot(t: Long, people: List<Long>, closed: Long? = null): PlanPhoto {
        val id = nextId++
        val faces = people.mapIndexed { i, p -> face(p, 0.05f + i * 0.13f, if (p == closed) EyeState.CLOSED else EyeState.OPEN) }
        return PlanPhoto(id = id, name = "IMG_%04d.jpg".format(id), folder = "Reception", takenAt = t, faces = faces,
            hash = id * 0x9E3779B97F4A7C15uL.toLong())
    }

    @Test
    fun eachFamilyOnStageIsOneGroupWithItsBestFrame() {
        val familyA = listOf(10L, 11L, 12L)
        val familyB = listOf(20L, 21L)
        val photos = listOf(
            shot(start, listOf(BRIDE, GROOM)),                                  // just the couple
            shot(start + 60 * second, listOf(BRIDE, GROOM) + familyA, closed = 11L),
            shot(start + 65 * second, listOf(BRIDE, GROOM) + familyA),           // the good frame
            shot(start + 70 * second, listOf(BRIDE, GROOM) + familyA, closed = 12L),
            shot(start + 180 * second, listOf(BRIDE, GROOM) + familyB),
            shot(start + 185 * second, listOf(BRIDE, GROOM) + familyB),
            shot(start + 200 * second, listOf(GROOM) + familyB),                // bride not in it
            shot(start + 3_600 * second, listOf(BRIDE, GROOM) + familyA),        // family A again, an hour later
        )
        val a = AlbumPlanner.analyze(photos, PeopleTags(BRIDE, GROOM))
        val groups = StageGroups.find(a)
        assertEquals(3, groups.size)
        assertEquals(familyA.toSet(), groups[0].guests)
        assertEquals(3, groups[0].photoIds.size)
        assertEquals(photos[2].id, groups[0].photoId)
        assertEquals(familyB.toSet(), groups[1].guests)
        assertEquals(2, groups[1].photoIds.size)
        assertEquals(listOf(photos[7].id), groups[2].photoIds)
        assertTrue(groups.zipWithNext().all { (x, y) -> x.order < y.order })
    }

    @Test
    fun noCoupleNoGroups() {
        val photos = listOf(shot(start, listOf(10L, 11L, 12L)))
        assertTrue(StageGroups.find(AlbumPlanner.analyze(photos, PeopleTags())).isEmpty())
    }
}
