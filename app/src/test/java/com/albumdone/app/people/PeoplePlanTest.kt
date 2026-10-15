package com.albumdone.app.people

import com.albumdone.app.plan.Box
import com.albumdone.app.plan.EyeState
import com.albumdone.app.plan.Expression
import com.albumdone.app.plan.Occasion
import com.albumdone.app.plan.PersonTag
import com.albumdone.app.plan.PlanFace
import com.albumdone.app.plan.PlanPhoto
import com.albumdone.app.plan.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeoplePlanTest {

    private var nextId = 1L
    private val start = 1_700_000_000_000L
    private val minute = 60_000L

    private fun photo(folder: String, t: Long, vararg people: Long) = PlanPhoto(
        id = nextId++, name = "IMG_%04d.jpg".format(nextId), folder = folder, takenAt = start + t * minute,
        faces = people.mapIndexed { i, p ->
            PlanFace(personId = p, box = Box(0.1f + 0.25f * i, 0.3f, 0.22f + 0.25f * i, 0.48f), eyes = EyeState.OPEN,
                expression = Expression.SMILE, smile = 0.7f, lookingAtCamera = true, directionX = 0f, sharpness = 100.0, brightness = 130.0)
        },
    )

    @Test
    fun aWeddingFindsTheCoupleAndTheBrideFromHerMehendi() {
        val photos = List(10) { photo("Mehendi", it * 10L, 1L, 5L) } +
            List(30) { photo("Wedding", 200L + it * 5, 1L, 2L) } +
            List(6) { photo("Wedding", 220L + it * 5, 1L, 2L, 5L) }
        val main = PeoplePlan.findMain(Occasion.WEDDING, photos)
        assertEquals(1L, main.first)
        assertEquals(2L, main.second)
    }

    @Test
    fun aChildsBirthdayIsAboutTheChildWithMummyAndPapa() {
        val star = 1L; val mummy = 2L; val papa = 3L
        val photos = List(40) { photo("", it * 6L, star) } +
            (0 until 8).flatMap { v -> listOf(photo("", v * 30L + 1, star, mummy), photo("", v * 30L + 2, star, papa), photo("", v * 30L + 3, mummy, star, papa)) } +
            (10L..20L).map { g -> photo("", 300L + g, star, g) }
        val main = PeoplePlan.findMain(Occasion.KIDS_BIRTHDAY, photos)
        assertEquals(star, main.first)
        assertNull(main.second)
        assertTrue(main.sure)
        val tags = PeoplePlan.tags(Occasion.KIDS_BIRTHDAY, photos, main.first, main.second)
        assertEquals(setOf(mummy, papa), tags.parents.toSet())
        assertEquals(Priority.MUST, tags.tag(mummy).priority)
        // When the family has named them, their names decide.
        val named = PeoplePlan.tags(Occasion.KIDS_BIRTHDAY, photos, star, null,
            userTags = mapOf(papa to PersonTag(papa, "Papa"), 12L to PersonTag(12L, "Mummy")), userTagged = setOf(papa, 12L))
        assertEquals(listOf(papa, 12L), named.parents)
        // A wedding has no parents list (each side has its own).
        assertTrue(PeoplePlan.tags(Occasion.WEDDING, photos, star, mummy).parents.isEmpty())
    }
}
