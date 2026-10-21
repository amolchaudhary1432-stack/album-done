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
        assertEquals(listOf(7, 2), people.take(2).map { it.photoCount })
        assertNotNull(project.brideId)
        assertNotNull(project.groomId)
        assertNotEquals(project.brideId, project.groomId)
        assertEquals(setOf(project.brideId, project.groomId), people.take(2).map { it.id }.toSet())

        val (planPhotos, tags) = PeopleRepository(dao).planInput(project)
        val analysis = AlbumPlanner.analyze(planPhotos, tags)
        DeviceTestSupport.writeReport(analysis, "analysis-report.txt")
        assertEquals(listOf("01 Haldi", "02 Wedding"), analysis.events)
        val list = AlbumPlanner.shortlist(analysis, 4)
        assertEquals(4, list.ids.size)
        assertTrue(list.ids.all { list.reasons[it].orEmpty().isNotEmpty() })
        assertTrue(list.ids.any { analysis.byId.getValue(it).event == "01 Haldi" })
        assertTrue(list.ids.any { analysis.byId.getValue(it).event == "02 Wedding" })
    }
}
