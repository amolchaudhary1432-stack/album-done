package com.albumdone.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.albumdone.app.data.AppDatabase
import com.albumdone.app.data.ProjectStatus
import com.albumdone.app.people.PeopleRepository
import com.albumdone.app.plan.AlbumPlanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The whole chain on a real Android system: worker, database, people, planner. */
@RunWith(AndroidJUnit4::class)
class PipelineDeviceTest {

    @Test
    fun analysesFindsTheCoupleAndPlansAnAlbum() = runBlocking {
        val id = TestWedding.createAndAnalyse("Pipeline test")
        val dao = AppDatabase.get(DeviceTestSupport.app).dao()

        val project = dao.project(id)!!
        assertEquals(ProjectStatus.DONE, project.status)
        val photos = dao.photos(id)
        assertEquals(TestWedding.PHOTOS.size, photos.size)
        assertTrue(photos.all { it.processed && !it.failed && it.thumbPath != null })

        // At least 1 face in each portrait and 2 in each photo of both men (a closer look may
        // also find small faces in the background).
        val faces = dao.faceRows(id)
        assertTrue("faces: ${faces.size}", faces.size >= 9)

        // The two main people, photographed together twice: the couple.
        val people = dao.people(id).sortedByDescending { it.photoCount }
        assertEquals(listOf(5, 4), people.take(2).map { it.photoCount })
        assertNotNull(project.brideId)
        assertNotNull(project.groomId)
        assertNotEquals(project.brideId, project.groomId)
        assertEquals(setOf(project.brideId, project.groomId), people.take(2).map { it.id }.toSet())

        val (planPhotos, tags) = PeopleRepository(dao).planInput(project)
        val analysis = AlbumPlanner.analyze(planPhotos, tags)
        writeReport(analysis)
        assertEquals(listOf("01 Haldi", "02 Wedding"), analysis.events)
        val list = AlbumPlanner.shortlist(analysis, 4)
        assertEquals(4, list.ids.size)
        assertTrue(list.ids.all { list.reasons[it].orEmpty().isNotEmpty() })
        assertTrue(list.ids.any { analysis.byId.getValue(it).event == "01 Haldi" })
        assertTrue(list.ids.any { analysis.byId.getValue(it).event == "02 Wedding" })
    }

    /** Every decision the app made about every photo, for Claude to review after the run. */
    private fun writeReport(analysis: com.albumdone.app.plan.AlbumAnalysis) {
        val text = buildString {
            appendLine("Couple: bride=${analysis.tags.brideId} groom=${analysis.tags.groomId}")
            appendLine("Tags: " + analysis.tags.tags.values.joinToString { "${it.id}:${it.priority}/${it.side}" })
            for (p in analysis.photos) {
                val ph = p.photo
                appendLine()
                appendLine("${ph.name} [${p.event}] ${ph.width}x${ph.height} shot=${p.shot} usable=${p.usable} quality=${"%.3f".format(p.quality)}")
                appendLine("  issues=${p.issues} people=${p.people} tilt=${ph.tilt} backs=${ph.foregroundBacks} phones=${ph.phones.size}")
                appendLine("  sharp=${"%.0f".format(ph.sharpness)} peak=${"%.0f".format(ph.peakSharpness)} bright=${"%.0f".format(ph.brightness)} dark=${"%.2f".format(ph.darkFraction)} blown=${"%.2f".format(ph.brightFraction)}")
                for (f in ph.faces) {
                    appendLine("  face person=${f.personId} box=${"%.2f,%.2f,%.2f,%.2f".format(f.box.left, f.box.top, f.box.right, f.box.bottom)} " +
                        "eyes=${f.eyes} expr=${f.expression} smile=${"%.2f".format(f.smile)} looking=${f.lookingAtCamera} dir=${"%.2f".format(f.directionX)} " +
                        "sharp=${"%.0f".format(f.sharpness)} bright=${"%.0f".format(f.brightness)} backlit=${f.backlit} occl=${"%.2f".format(f.occlusion)} cut=${"%.2f".format(f.cutOff)}")
                }
            }
        }
        val dir = java.io.File(DeviceTestSupport.app.filesDir, "screens")
        dir.mkdirs()
        java.io.File(dir, "analysis-report.txt").writeText(text)
    }
}
