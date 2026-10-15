package com.albumdone.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Insert
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun project(id: Long): ProjectEntity?

    @Query(
        """
        SELECT p.*,
          (SELECT COUNT(*) FROM photos WHERE projectId = p.id) AS total,
          (SELECT COUNT(*) FROM photos WHERE projectId = p.id AND processed = 1) AS done
        FROM projects p ORDER BY createdAt DESC
        """
    )
    fun projectSummaries(): Flow<List<ProjectSummary>>

    @Query(
        """
        SELECT p.*,
          (SELECT COUNT(*) FROM photos WHERE projectId = p.id) AS total,
          (SELECT COUNT(*) FROM photos WHERE projectId = p.id AND processed = 1) AS done
        FROM projects p WHERE p.id = :id
        """
    )
    fun projectSummary(id: Long): Flow<ProjectSummary?>

    @Query("UPDATE projects SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("UPDATE projects SET activeMillis = :millis WHERE id = :id")
    suspend fun setActiveMillis(id: Long, millis: Long)

    @Query("UPDATE projects SET albumSize = :size WHERE id = :id")
    suspend fun setAlbumSize(id: Long, size: Int)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: Long)

    @Query("DELETE FROM photos WHERE projectId = :id")
    suspend fun deletePhotos(id: Long)

    @Insert
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Query("SELECT * FROM photos WHERE projectId = :projectId AND processed = 0 ORDER BY folder, name")
    suspend fun pendingPhotos(projectId: Long): List<PhotoEntity>

    @Query("SELECT COUNT(*) FROM photos WHERE projectId = :projectId")
    suspend fun countAll(projectId: Long): Int

    @Query("SELECT COUNT(*) FROM photos WHERE projectId = :projectId AND processed = 1")
    suspend fun countProcessed(projectId: Long): Int

    @Update
    suspend fun updatePhoto(photo: PhotoEntity)

    @Query("SELECT * FROM photos WHERE projectId = :projectId ORDER BY folder, name")
    suspend fun photos(projectId: Long): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE projectId = :projectId ORDER BY folder, name")
    fun photosFlow(projectId: Long): Flow<List<PhotoEntity>>

    @Query("UPDATE photos SET selected = 0 WHERE projectId = :projectId")
    suspend fun clearSelection(projectId: Long)

    @Query("UPDATE photos SET removed = 0 WHERE projectId = :projectId")
    suspend fun clearRemoved(projectId: Long)

    @Query("UPDATE photos SET selected = 1 WHERE id IN (:ids)")
    suspend fun markSelected(ids: List<Long>)

    @Query("UPDATE photos SET selected = 0, removed = 1 WHERE id = :id")
    suspend fun removeFromShortlist(id: Long)

    @Query("UPDATE photos SET removed = 0 WHERE id = :id")
    suspend fun undoRemove(id: Long)

    @Query("UPDATE photos SET removed = 0, selected = 1 WHERE id = :id")
    suspend fun restoreToShortlist(id: Long)

    /** Saves a new shortlist. Ids are written in chunks to stay under SQLite's variable limit. */
    @Transaction
    suspend fun replaceSelection(projectId: Long, ids: List<Long>) {
        clearSelection(projectId)
        ids.chunked(500).forEach { markSelected(it) }
    }

    @Transaction
    suspend fun deleteProjectWithPhotos(id: Long) {
        deletePhotos(id)
        deleteProject(id)
    }
}
