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
        deleteFaces(id)
        deletePeople(id)
        deletePhotos(id)
        deleteProject(id)
    }

    // ---- Analysis results ----

    @Insert
    suspend fun insertFaces(faces: List<FaceEntity>)

    @Query("DELETE FROM faces WHERE photoId = :photoId")
    suspend fun deleteFacesOfPhoto(photoId: Long)

    /** Saves one analysed photo and its faces together, so a stopped run never leaves half a photo. */
    @Transaction
    suspend fun savePhotoResult(photo: PhotoEntity, faces: List<FaceEntity>) {
        deleteFacesOfPhoto(photo.id)
        if (faces.isNotEmpty()) insertFaces(faces)
        updatePhoto(photo)
    }

    @Query("DELETE FROM faces WHERE projectId = :id")
    suspend fun deleteFaces(id: Long)

    @Query(
        """
        SELECT id, photoId, `left`, top, `right`, bottom, eyes, expression, smile, looking, directionX,
               sharpness, brightness, backlit, occlusion, cutOff, quality, personId
        FROM faces WHERE projectId = :projectId
        """
    )
    suspend fun faceRows(projectId: Long): List<FaceRow>

    @Query("SELECT id, photoId, quality, embedding, personId FROM faces WHERE projectId = :projectId AND embedding IS NOT NULL")
    suspend fun faceEmbeddings(projectId: Long): List<FaceEmbeddingRow>

    @Query("SELECT id, photoId, quality, embedding, personId FROM faces WHERE personId = :personId AND embedding IS NOT NULL")
    suspend fun faceEmbeddingsOf(personId: Long): List<FaceEmbeddingRow>

    @Query("UPDATE faces SET personId = :personId WHERE id IN (:faceIds)")
    suspend fun setPerson(faceIds: List<Long>, personId: Long?)

    @Query("UPDATE faces SET personId = NULL WHERE projectId = :projectId")
    suspend fun clearPeopleOfFaces(projectId: Long)

    @Query("UPDATE faces SET personId = :into WHERE personId = :from")
    suspend fun moveFaces(from: Long, into: Long)

    // ---- People ----

    @Insert
    suspend fun insertPerson(person: PersonEntity): Long

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun person(id: Long): PersonEntity?

    @Query("SELECT * FROM people WHERE projectId = :projectId")
    suspend fun people(projectId: Long): List<PersonEntity>

    @Query(
        """
        SELECT pe.*, ph.thumbPath AS thumbPath, f.`left` AS faceLeft, f.top AS faceTop,
               f.`right` AS faceRight, f.bottom AS faceBottom
        FROM people pe
        LEFT JOIN faces f ON f.id = pe.bestFaceId
        LEFT JOIN photos ph ON ph.id = f.photoId
        WHERE pe.projectId = :projectId AND pe.photoCount > 0
        ORDER BY pe.photoCount DESC
        """
    )
    fun peopleWithFaces(projectId: Long): Flow<List<PersonWithFace>>

    @Query("DELETE FROM people WHERE projectId = :id")
    suspend fun deletePeople(id: Long)

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deletePerson(id: Long)

    @Query(
        """
        SELECT personId, COUNT(DISTINCT photoId) AS photos FROM faces
        WHERE projectId = :projectId AND personId IS NOT NULL AND (`right` - `left`) >= 0.03
        GROUP BY personId
        """
    )
    suspend fun personPhotoCounts(projectId: Long): List<PersonCount>

    @Query(
        """
        SELECT id FROM faces WHERE personId = :personId
        ORDER BY ((`right` - `left`) * quality) DESC LIMIT 1
        """
    )
    suspend fun bestFaceOf(personId: Long): Long?

    @Query("UPDATE people SET photoCount = :count, bestFaceId = :bestFace WHERE id = :id")
    suspend fun setPersonStats(id: Long, count: Int, bestFace: Long?)

    @Query("UPDATE projects SET brideId = :bride, groomId = :groom WHERE id = :id")
    suspend fun setCouple(id: Long, bride: Long?, groom: Long?)

    @Query("UPDATE projects SET familyTagged = 1 WHERE id = :id")
    suspend fun setFamilyTagged(id: Long)

    @Query("UPDATE projects SET coverPhotoId = :photoId WHERE id = :id")
    suspend fun setCover(id: Long, photoId: Long?)

    @Query("UPDATE photos SET pickReason = :reason WHERE id = :id")
    suspend fun setPickReason(id: Long, reason: String?)

    @Transaction
    suspend fun setPickReasons(reasons: Map<Long, String>) {
        reasons.forEach { (id, r) -> setPickReason(id, r) }
    }
}
