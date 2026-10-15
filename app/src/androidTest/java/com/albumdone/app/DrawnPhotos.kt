package com.albumdone.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import com.albumdone.app.data.AlbumDao
import com.albumdone.app.data.FaceEntity
import com.albumdone.app.data.PhotoEntity
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * Made-up photos for the phone tests that check people and screens at scale: each photo is drawn
 * (coloured faces with a label) and stored as if the app had already checked it, faces and all.
 */
object DrawnPhotos {

    /** Draws and stores one photo per shot: (people in it, minute of the day). Empty people: decor. */
    suspend fun store(dao: AlbumDao, projectId: Long, shots: List<Pair<List<Long>, Int>>, labels: Map<Long, String>) {
        val app = DeviceTestSupport.app
        val hues = HashMap<Long, Float>().apply { labels.keys.forEachIndexed { i, id -> put(id, (i * 137.5f) % 360f) } }
        val dir = File(app.filesDir, "thumbs/$projectId").apply { mkdirs() }
        val start = 1_700_000_000_000L
        val random = Random(7)
        val photos = shots.mapIndexed { n, (people, minute) ->
            val file = File(dir, "IMG_%04d.jpg".format(n + 1))
            draw(people, labels, hues, minute, file)
            PhotoEntity(
                projectId = projectId, docUri = Uri.fromFile(file).toString(), name = file.name, folder = "", size = file.length(),
                lastModified = 0, processed = true, takenAt = start + minute * 60_000L + n * 4_000L, width = 1200, height = 800,
                sharpness = 300.0, peakSharpness = 600.0, brightness = 125.0, darkFraction = 0.03, brightFraction = 0.01,
                hash = random.nextLong(), faceCount = people.size, peopleArea = (0.1f * people.size).coerceAtMost(0.8f),
                thumbPath = file.path,
            )
        }
        dao.insertPhotos(photos)
        val stored = dao.photos(projectId).associateBy { it.name }
        val faces = shots.flatMapIndexed { n, (people, _) ->
            val photoId = stored.getValue("IMG_%04d.jpg".format(n + 1)).id
            people.mapIndexed { k, person ->
                val box = faceBox(k, people.size)
                FaceEntity(
                    projectId = projectId, photoId = photoId, left = box[0], top = box[1], right = box[2], bottom = box[3],
                    eyes = "OPEN", expression = "SMILE", smile = 0.8f, looking = true, directionX = 0f, yaw = 0f,
                    sharpness = 400.0, brightness = 130.0, backlit = false, occlusion = 0f, cutOff = 0f, quality = 0.8f, personId = person,
                )
            }
        }
        faces.chunked(500).forEach { dao.insertFaces(it) }
        for ((id, count) in dao.personPhotoCounts(projectId).map { it.personId to it.photos }) dao.setPersonStats(id, count, dao.bestFaceOf(id))
    }

    /** Faces side by side across the middle of the photo. */
    private fun faceBox(k: Int, n: Int): FloatArray {
        val w = if (n <= 3) 0.16f else 0.11f
        val gap = (1f - n * w) / (n + 1)
        val left = gap + k * (w + gap)
        return floatArrayOf(left, 0.28f, left + w, 0.28f + w * 1.5f)
    }

    private fun draw(people: List<Long>, labels: Map<Long, String>, hues: Map<Long, Float>, minute: Int, out: File) {
        val w = 1200; val h = 800
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.HSVToColor(floatArrayOf((minute * 0.6f) % 360f, 0.25f, 0.85f)))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        if (people.isEmpty()) {
            paint.color = Color.HSVToColor(floatArrayOf(40f, 0.6f, 0.7f))
            c.drawRect(w * 0.3f, h * 0.3f, w * 0.7f, h * 0.7f, paint) // decor
        }
        people.forEachIndexed { k, person ->
            val b = faceBox(k, people.size)
            val cx = (b[0] + b[2]) / 2f * w; val cy = (b[1] + b[3]) / 2f * h
            val r = (b[2] - b[0]) * w / 2f
            paint.color = Color.HSVToColor(floatArrayOf(hues.getValue(person), 0.55f, 0.75f))
            c.drawCircle(cx, cy, r, paint)
            c.drawRect(cx - r * 1.2f, cy + r * 1.1f, cx + r * 1.2f, h.toFloat(), paint) // shoulders
            text.textSize = r * 0.8f
            c.drawText(labels.getValue(person), cx, cy + r * 0.28f, text)
        }
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()
    }
}
