package com.albumdone.app.plan

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sqrt
import kotlin.random.Random

class AlbumMindTest {

    private val weights = AlbumMind.read(File("src/main/assets/models/album_mind.bin").readBytes())

    @After
    fun noBrain() { AlbumMind.weights = null }

    private fun unit(v: FloatArray): FloatArray { val n = sqrt(v.sumOf { it.toDouble() * it }).toFloat(); return FloatArray(v.size) { v[it] / n } }

    /**
     * One event: a long moment (30 photos, 2 s apart, six poses of five frames each: frames of one pose
     * look nearly the same), then, 20 minutes later, a short moment of 3 photos.
     */
    private fun folder(withScenes: Boolean = true): Pair<List<PlanPhoto>, Map<Long, Int>> {
        val r = Random(7)
        val poses = List(7) { unit(FloatArray(768) { r.nextFloat() - 0.5f }) }
        val poseOf = HashMap<Long, Int>()
        val photos = ArrayList<PlanPhoto>()
        var id = 1L
        for (k in 0 until 30) {
            val pose = k / 5
            val v = unit(FloatArray(768) { poses[pose][it] + 0.02f * (r.nextFloat() - 0.5f) })
            poseOf[id] = pose
            photos += PlanPhoto(id, "IMG_%04d.jpg".format(id), "Haldi", takenAt = 1_000_000L + k * 2_000L, hash = r.nextLong(),
                sharpness = 100.0 + k, peakSharpness = 100.0 + k, scene = if (withScenes) v else null)
            id++
        }
        for (k in 0 until 3) {
            val v = unit(FloatArray(768) { poses[6][it] + 0.3f * (r.nextFloat() - 0.5f) })
            poseOf[id] = 6
            photos += PlanPhoto(id, "IMG_%04d.jpg".format(id), "Haldi", takenAt = 1_000_000L + 20 * 60_000L + k * 30_000L, hash = r.nextLong(),
                scene = if (withScenes) v else null)
            id++
        }
        return photos to poseOf
    }

    @Test
    fun theLearnedNumbersMatchWhatTheAppMeasures() {
        val a = AlbumPlanner.analyze(folder().first)
        assertEquals(Appeal.features(a.photos[0], a, 100.0, 128.0).keys.toList(), weights.feat)
        assertTrue(weights.decay in 0.1..0.9 && weights.beatLevel in 0.5..0.99 && weights.nearCopy in 0.5..0.99)
    }

    @Test
    fun aLongMomentGivesEachPoseItsPlaceBeforeRepeatingOne() {
        val (photos, poseOf) = folder()
        val a = AlbumPlanner.analyze(photos)
        val picks = AlbumMind.choose(a, AlbumMind.vectors(a), weights, 8)
        assertEquals(8, picks.toSet().size)
        val long = picks.filter { poseOf.getValue(it) < 6 }
        assertTrue("the long moment gets more than one photo: $picks", long.size >= 2)
        assertEquals("one photo per pose: $picks", long.size, long.map { poseOf.getValue(it) }.toSet().size)
    }

    @Test
    fun neverTwoNearCopiesOfOneMoment() {
        val (photos, _) = folder()
        val a = AlbumPlanner.analyze(photos)
        val v = AlbumMind.vectors(a)
        val picks = AlbumMind.choose(a, v, weights, 12)
        for (x in picks) for (y in picks) {
            if (x >= y || a.byId.getValue(x).momentId != a.byId.getValue(y).momentId) continue
            val cos = v.getValue(x).indices.sumOf { v.getValue(x)[it].toDouble() * v.getValue(y)[it] }
            assertTrue("near-copies $x and $y ($cos)", cos < weights.nearCopy)
        }
    }

    @Test
    fun keepsTheCouplesChoicesAndNeverBringsBackARemovedPhoto() {
        val (photos, _) = folder()
        val a = AlbumPlanner.analyze(photos)
        val first = AlbumMind.choose(a, AlbumMind.vectors(a), weights, 6)
        val removed = setOf(first[0])
        val keep = setOf(photos.last().id)
        val again = AlbumMind.choose(a, AlbumMind.vectors(a), weights, 6, removed, keep)
        assertTrue(keep.all { it in again })
        assertFalse(first[0] in again)
        assertEquals(6, again.size)
        assertEquals("same folder, same album", again, AlbumMind.choose(a, AlbumMind.vectors(a), weights, 6, removed, keep))
    }

    @Test
    fun theAlbumUsesTheBrainOnlyWhenItHasSeenThePhotos() {
        AlbumMind.weights = weights
        val seen = AlbumPlanner.analyze(folder().first)
        assertTrue(AlbumMind.canSee(seen))
        val list = AlbumPlanner.shortlist(seen, 8)
        assertEquals(8, list.ids.size)
        assertTrue(list.reasons.values.flatten().any { it.contains("moment") || it.contains("story") })
        assertTrue("story order", list.ids.map { seen.byId.getValue(it).order }.zipWithNext().all { (x, y) -> x < y })

        val notSeen = AlbumPlanner.analyze(folder(withScenes = false).first)
        assertFalse(AlbumMind.canSee(notSeen))
        assertEquals(AlbumPlanner.rules(notSeen, 8).ids, AlbumPlanner.shortlist(notSeen, 8).ids)
    }
}
