package com.albumdone.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object ProjectStatus {
    const val ANALYZING = "analyzing"
    /** All photos checked; grouping faces into people. */
    const val PEOPLE = "people"
    const val DONE = "done"
}

/** One wedding = one project. */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val treeUri: String,
    val createdAt: Long,
    val status: String = ProjectStatus.ANALYZING,
    /** Time spent actually analysing (excludes time the app was stopped). */
    val activeMillis: Long = 0,
    val albumSize: Int = 300,
    val brideId: Long? = null,
    val groomId: Long? = null,
    val coverPhotoId: Long? = null,
    /** The couple has been through the "Tag your family" screen. */
    val familyTagged: Boolean = false,
)

/** One photo inside the chosen folder, plus everything we measured about it. */
@Entity(
    tableName = "photos",
    indices = [Index("projectId"), Index(value = ["projectId", "processed"])],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val docUri: String,
    val name: String,
    /** Sub-folder path inside the chosen folder, e.g. "Haldi". Empty for the top folder. */
    val folder: String,
    val size: Long,
    val lastModified: Long,
    val processed: Boolean = false,
    val failed: Boolean = false,
    /** Capture time from the camera, or 0 when unknown. */
    val takenAt: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val sharpness: Double = 0.0,
    val peakSharpness: Double = 0.0,
    val brightness: Double = 0.0,
    val darkFraction: Double = 0.0,
    val brightFraction: Double = 0.0,
    val hash: Long = 0,
    val tilt: Float = 0f,
    val faceCount: Int = 0,
    /** Phones seen, as "l,t,r,b;l,t,r,b". */
    val phones: String = "",
    val foregroundBacks: Int = 0,
    val thumbPath: String? = null,
    val selected: Boolean = false,
    val removed: Boolean = false,
    /** Why the planner picked this photo, one reason per line. */
    val pickReason: String? = null,
)

/** One face in one photo. Box values are 0..1 of the photo. */
@Entity(
    tableName = "faces",
    indices = [Index("projectId"), Index("photoId"), Index("personId")],
)
data class FaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val photoId: Long,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val eyes: String,
    val expression: String,
    val smile: Float,
    val looking: Boolean,
    val directionX: Float,
    val yaw: Float,
    val sharpness: Double,
    val brightness: Double,
    val backlit: Boolean,
    val occlusion: Float,
    val cutOff: Float,
    /** 0..1: how reliable this face is for recognising the person. */
    val quality: Float,
    /** SFace fingerprint: 128 floats, or null for faces too small to recognise. */
    val embedding: ByteArray? = null,
    val personId: Long? = null,
)

/** A face without its fingerprint, for loading many faces cheaply. */
data class FaceRow(
    val id: Long,
    val photoId: Long,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val eyes: String,
    val expression: String,
    val smile: Float,
    val looking: Boolean,
    val directionX: Float,
    val sharpness: Double,
    val brightness: Double,
    val backlit: Boolean,
    val occlusion: Float,
    val cutOff: Float,
    val quality: Float,
    val personId: Long?,
)

data class FaceEmbeddingRow(
    val id: Long,
    val photoId: Long,
    val quality: Float,
    val embedding: ByteArray,
    val personId: Long?,
)

object PersonPriority {
    const val MUST = "MUST"
    const val IMPORTANT = "IMPORTANT"
    const val NORMAL = "NORMAL"
    const val SKIP = "SKIP"
}

object PersonSide {
    const val BRIDE = "BRIDE"
    const val GROOM = "GROOM"
    const val UNKNOWN = "UNKNOWN"
}

/** One person found across the wedding (a group of matching faces). */
@Entity(tableName = "people", indices = [Index("projectId")])
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String = "",
    val priority: String = PersonPriority.NORMAL,
    val side: String = PersonSide.UNKNOWN,
    val elder: Boolean = false,
    val photoCount: Int = 0,
    /** The clearest face of this person, shown on the tagging screen. */
    val bestFaceId: Long? = null,
)

data class PersonCount(val personId: Long, val photos: Int)

/** A person with the photo and box of their clearest face, for showing a small face picture. */
data class PersonWithFace(
    @Embedded val person: PersonEntity,
    val thumbPath: String?,
    val faceLeft: Float?,
    val faceTop: Float?,
    val faceRight: Float?,
    val faceBottom: Float?,
)

data class ProjectSummary(
    @Embedded val project: ProjectEntity,
    val total: Int,
    val done: Int,
)
