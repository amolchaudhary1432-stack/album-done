package com.albumdone.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object ProjectStatus {
    const val ANALYZING = "analyzing"
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
    val brightness: Double = 0.0,
    val darkFraction: Double = 0.0,
    val brightFraction: Double = 0.0,
    val hash: Long = 0,
    val faceCount: Int = 0,
    /** Best smile among clearly visible faces, 0..1, or -1 when there are none. */
    val smile: Float = -1f,
    val eyesClosed: Boolean = false,
    val thumbPath: String? = null,
    val selected: Boolean = false,
    val removed: Boolean = false,
)

data class ProjectSummary(
    @Embedded val project: ProjectEntity,
    val total: Int,
    val done: Int,
)
