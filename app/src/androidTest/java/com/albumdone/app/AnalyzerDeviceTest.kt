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
            for (name in listOf("obama.jpg", "obama2.jpg", "obama_small.jpg", "biden.jpg", "two_people.jpg")) {
                val thumb = File(DeviceTestSupport.app.cacheDir, "test-thumbs/$name")
                val started = System.currentTimeMillis()
                results[name] = analyzer.analyze(DeviceTestSupport.photo(name), thumb)
                val took = System.currentTimeMillis() - started
                Log.i("AlbumDoneTest", "$name analysed in $took ms: ${results[name]!!.faces.size} faces")
                assertTrue("thumbnail written for $name", thumb.length() > 0)
            }
        }

        @JvmStatic @AfterClass
        fun close() = analyzer.close()
    }

    private fun only(name: String) = results.getValue(name).faces.single()

    @Test
    fun findsEveryFace() {
        assertEquals(1, results.getValue("obama.jpg").faces.size)
        assertEquals(1, results.getValue("biden.jpg").faces.size)
        assertEquals(2, results.getValue("two_people.jpg").faces.size)
    }

    @Test
    fun readsExpressionsLikeAPerson() {
        val portrait = only("obama.jpg").reading
        assertEquals(EyeState.OPEN, portrait.eyes)
        assertEquals(Expression.SMILE, portrait.expression)
        assertTrue(portrait.lookingAtCamera)
        assertEquals(0f, portrait.cutOff, 0.01f)
        // Every face in these photos has open eyes.
        for ((name, r) in results) for (f in r.faces) assertTrue("$name eyes ${f.reading.eyes}", f.reading.eyes != EyeState.CLOSED)
    }

    @Test
    fun recognisesTheSamePersonAndTellsPeopleApart() {
        val obama = only("obama.jpg").embedding!!
        val obamaAgain = only("obama2.jpg").embedding!!
        val obamaSmall = only("obama_small.jpg").embedding!!
        val biden = only("biden.jpg").embedding!!
        assertTrue(PeopleClusterer.cosine(obama, obamaAgain) > PeopleClusterer.JOIN)
        assertTrue(PeopleClusterer.cosine(obama, obamaSmall) > PeopleClusterer.JOIN)
        assertTrue(PeopleClusterer.cosine(obama, biden) < 0.3f)
        // In the photo of both, the face on the left is Obama, the one on the right is Biden.
        val (left, right) = results.getValue("two_people.jpg").faces.sortedBy { it.reading.box.left }
        assertTrue(PeopleClusterer.cosine(obama, left.embedding!!) > PeopleClusterer.JOIN)
        assertTrue(PeopleClusterer.cosine(biden, right.embedding!!) > PeopleClusterer.JOIN)
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
        assertTrue(results.getValue("obama.jpg").let { it.height > it.width })
        assertTrue(results.getValue("two_people.jpg").let { it.width > it.height })
    }

    @Test
    fun peopleAreGroupedCorrectly() {
        val faces = results.values.flatMap { r -> r.faces }.mapIndexed { i, f ->
            com.albumdone.app.people.FaceVector(i.toLong(), i.toLong(), f.embedding!!, maxOf(f.quality, 0.5f))
        }
        val groups = PeopleClusterer.cluster(faces)
        assertEquals(listOf(4, 2), groups.map { it.size })
    }
}
