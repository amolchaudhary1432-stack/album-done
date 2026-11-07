package com.albumdone.app.people

import com.albumdone.app.plan.AlbumMind
import com.albumdone.app.plan.Box
import com.albumdone.app.plan.PlanFace
import com.albumdone.app.plan.PlanPhoto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Which of the couple is the bride, from what the brain's eyes see in each one's own photos. */
class BrideByEyeTest {

    private val weights = AlbumMind.read(File("src/main/assets/models/album_mind.bin").readBytes())

    @After
    fun noBrain() { AlbumMind.weights = null }

    /** A photo of [person] alone that looks like SigLIP's "woman" or "man" text, with some noise. */
    private fun solo(id: Long, person: Long, looksLike: String, r: Random): PlanPhoto {
        val t = weights.vec(looksLike)
        val v = FloatArray(t.size) { (t[it] + 0.02 * (r.nextDouble() - 0.5)).toFloat() }
        return PlanPhoto(id, "IMG_$id.jpg", "", faces = listOf(PlanFace(personId = person, box = Box(0.4f, 0.2f, 0.55f, 0.4f))), scene = v)
    }

    private val byPhoto = { photos: List<PlanPhoto> -> photos.associate { p -> p.id to p.faces.map { p.id to it } } }

    @Test
    fun theWomanIsTheBrideEvenWhenTheGroomIsPhotographedMore() {
        AlbumMind.weights = weights
        val r = Random(3)
        // Person 7 (a man, his own haldi folder) is in more photos than person 9 (a woman).
        val photos = (1L..12L).map { solo(it, 7, "man", r) } + (13L..17L).map { solo(it, 9, "woman", r) }
        assertEquals(9L to 7L, PeoplePlan.brideFirst(7L to 9L, photos, byPhoto(photos)))
        assertEquals(9L to 7L, PeoplePlan.brideFirst(9L to 7L, photos, byPhoto(photos)))
    }

    @Test
    fun unchangedWithoutTheBrainOrWithTooFewPhotos() {
        val r = Random(4)
        val photos = (1L..5L).map { solo(it, 7, "man", r) } + (6L..10L).map { solo(it, 9, "woman", r) }
        assertEquals("no brain", 7L to 9L, PeoplePlan.brideFirst(7L to 9L, photos, byPhoto(photos)))
        AlbumMind.weights = weights
        val few = photos.filter { it.faces[0].personId == 7L } + photos.filter { it.faces[0].personId == 9L }.take(2)
        assertEquals("two photos of her alone are not enough", 7L to 9L, PeoplePlan.brideFirst(7L to 9L, few, byPhoto(few)))
    }
}
