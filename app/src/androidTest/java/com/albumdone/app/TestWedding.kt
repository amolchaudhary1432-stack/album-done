package com.albumdone.app

import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.albumdone.app.data.AppDatabase
import com.albumdone.app.data.PhotoEntity
import com.albumdone.app.data.ProjectEntity
import com.albumdone.app.work.AnalyzeWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue

/**
 * A small wedding made of freely licensed Bengali wedding photos in two event folders, analysed by the real
 * background worker exactly as on a phone.
 */
object TestWedding {

    /** (file in test assets, event folder, name in the wedding folder): a Bengali bride in 7, a man in 2. */
    val PHOTOS = listOf(
        Triple("bride_haldi.jpg", "01 Haldi", "IMG_0001.jpg"),
        Triple("bride_adhibaas.jpg", "01 Haldi", "IMG_0002.jpg"),
        Triple("two_people.jpg", "01 Haldi", "IMG_0003.jpg"),
        Triple("bride.jpg", "02 Wedding", "IMG_0101.jpg"),
        Triple("bride_small.jpg", "02 Wedding", "IMG_0102.jpg"),
        Triple("two_people.jpg", "02 Wedding", "IMG_0103.jpg"),
        Triple("bride_again.jpg", "02 Wedding", "IMG_0104.jpg"),
    )

    /** Creates the album as the app does after a folder is chosen; nothing is checked yet. */
    fun create(name: String, albumSize: Int = 4): Long = runBlocking {
        val dao = AppDatabase.get(DeviceTestSupport.app).dao()
        val id = dao.insertProject(ProjectEntity(name = name, treeUri = "file:///test", createdAt = System.currentTimeMillis(), albumSize = albumSize))
        dao.insertPhotos(PHOTOS.map { (asset, folder, fileName) ->
            PhotoEntity(
                projectId = id,
                docUri = DeviceTestSupport.photo(asset, "$name/$folder", fileName).toString(),
                name = fileName,
                folder = folder,
                size = 1,
                lastModified = 0,
            )
        })
        id
    }

    /** Creates the album, runs the analysis worker to the end and returns the album id. */
    fun createAndAnalyse(name: String): Long = runBlocking {
        val id = create(name)
        val worker = TestListenableWorkerBuilder<AnalyzeWorker>(DeviceTestSupport.app)
            .setInputData(workDataOf(AnalyzeWorker.KEY_PROJECT_ID to id))
            .build()
        val result = worker.doWork()
        assertTrue("analysis finished: $result", result is ListenableWorker.Result.Success)
        id
    }
}
