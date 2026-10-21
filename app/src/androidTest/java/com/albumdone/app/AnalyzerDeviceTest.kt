package com.albumdone.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.albumdone.app.analysis.PhotoAnalysis
import com.albumdone.app.analysis.PhotoAnalyzer
import com.albumdone.app.people.PeopleClusterer
import com.albumdone.app.plan.EyeState
import com.albumdone.app.plan.Expression
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the real photo analysis (MediaPipe face landmarks, object detector, SFace face matching)
 * on real photos, on an Android system.
 */
@RunWith(AndroidJUnit4::class)
class AnalyzerDeviceTest {

    companion object {
        private lateinit var analyzer: PhotoAnalyzer
        private val results = HashMap<String, PhotoAnalysis>()

        @JvmStatic @BeforeClass
        fun analyzeAll() {
            analyzer = PhotoAnalyzer(DeviceTestSupport.app)
            for (name in listOf("bride.jpg", "bride_again.jpg", "bride_small.jpg", "bride_haldi.jpg", "bride_adhibaas.jpg", "two_people.jpg")) {
                val thumb = File(DeviceTestSupport.app.cacheDir, "test-thumbs/$name")
                val started = System.currentTimeMillis()
                results[name] = analyzer.analyze(DeviceTestSupport.photo(name), thumb)
                val took = System.currentTimeMillis() - started
                Log.i("AlbumDoneTest", "$name analysed in $took ms: ${results[name]!!.faces.size} faces")
                assertTrue("thumbnail written for $name", thumb.length() > 0)
            }
        }

        @JvmStatic @AfterClass
        fun close() {
            if (::analyzer.isInitialized) analyzer.close()
        }
    }

    private fun only(name: String) = results.getValue(name).faces.single()

    @Test
    fun findsEveryFace() {
        assertEquals(1, results.getValue("bride.jpg").faces.size)
        assertEquals(1, results.getValue("bride_haldi.jpg").faces.size)
        assertEquals(2, results.getValue("two_people.jpg").faces.size)
    }

    @Test
    fun readsExpressionsLikeAPerson() {
        // The bride at her haldi, smiling into the camera.
        val portrait = only("bride_haldi.jpg").reading
        assertEquals(EyeState.OPEN, portrait.eyes)
        assertEquals(Expression.SMILE, portrait.expression)
        assertTrue(portrait.lookingAtCamera)
        assertEquals(0f, portrait.cutOff, 0.01f)
        // Every face in these photos has open eyes.
        for ((name, r) in results) for (f in r.faces) assertTrue("$name eyes ${f.reading.eyes}", f.reading.eyes != EyeState.CLOSED)
    }

    @Test
    fun recognisesTheSamePersonAndTellsPeopleApart() {
        // The same Bengali bride in bridal make-up, from another side, small, and at her haldi.
        val bride = only("bride.jpg").embedding!!
        assertTrue(PeopleClusterer.cosine(bride, only("bride_again.jpg").embedding!!) > PeopleClusterer.JOIN)
        assertTrue(PeopleClusterer.cosine(bride, only("bride_small.jpg").embedding!!) > PeopleClusterer.JOIN)
        assertTrue(PeopleClusterer.cosine(bride, only("bride_haldi.jpg").embedding!!) > PeopleClusterer.FIND_MORE)
        // In the photo of two, the bride is on the left; the man beside her is someone else.
        val (left, right) = results.getValue("two_people.jpg").faces.sortedBy { it.reading.box.left }
        assertTrue(PeopleClusterer.cosine(bride, left.embedding!!) > PeopleClusterer.JOIN)
        assertTrue(PeopleClusterer.cosine(bride, right.embedding!!) < 0.3f)
    }

    @Test
    fun measuresTheWholePhoto() {
        for ((name, r) in results) {
            assertTrue("$name brightness", r.quality.brightness in 20.0..240.0)
            assertTrue("$name sharpness", r.peakSharpness > 0.0)
            assertTrue("$name size", r.width > 0 && r.height > 0)
            assertNotNull(r.faces.firstOrNull()?.embedding)
        }
        // Portrait photos are taller than wide.
        assertTrue(results.getValue("bride.jpg").let { it.height > it.width })
        assertTrue(results.getValue("bride_haldi.jpg").let { it.width > it.height })
    }

    @Test
    fun peopleAreGroupedCorrectly() {
        // One id per face, one photo id per photo (two faces in one photo are two people).
        val names = results.keys.sorted()
        val manFace = results.getValue("two_people.jpg").faces.maxBy { it.reading.box.left }
        var man = -1L
        val faces = names.flatMap { name -> results.getValue(name).faces.map { f -> name to f } }.mapIndexed { i, (name, f) ->
            if (f === manFace) man = i.toLong()
            com.albumdone.app.people.FaceVector(i.toLong(), names.indexOf(name).toLong(), f.embedding!!, maxOf(f.quality, 0.5f))
        }
        val groups = PeopleClusterer.cluster(faces)
        // The bride's six faces are one person, and the man is not in it.
        assertEquals(6, groups.first().size)
        assertTrue(man !in groups.first())
    }
}
