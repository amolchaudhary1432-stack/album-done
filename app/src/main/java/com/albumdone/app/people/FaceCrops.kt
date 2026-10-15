package com.albumdone.app.people

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.albumdone.app.data.AlbumDao
import com.albumdone.app.plan.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Sharp face pictures for the family strip, cut from the original photos. The 480px thumbnails
 * are too small for faces at the back of a group, and a blurry face is hard to recognise when the
 * couple is asked "Who is this?".
 */
object FaceCrops {
    const val SIDE = 192

    fun file(context: Context, projectId: Long, personId: Long) = File(context.filesDir, "faces/$projectId/$personId.jpg")

    fun deleteAll(context: Context, projectId: Long) = File(context.filesDir, "faces/$projectId").deleteRecursively()

    /** One picture per person (the [limit] most photographed), from their best face. */
    suspend fun saveAll(context: Context, dao: AlbumDao, projectId: Long, limit: Int = 60) = withContext(Dispatchers.IO) {
        deleteAll(context, projectId)
        val faces = dao.faceRows(projectId).associateBy { it.id }
        val photos = dao.photos(projectId).associateBy { it.id }
        val people = dao.people(projectId).filter { it.bestFaceId != null }.sortedByDescending { it.photoCount }.take(limit)
        for (p in people) {
            val f = faces[p.bestFaceId] ?: continue
            val photo = photos[f.photoId] ?: continue
            try {
                save(context, Uri.parse(photo.docUri), Box(f.left, f.top, f.right, f.bottom), file(context, projectId, p.id))
            } catch (_: Exception) {
                // The avatar falls back to the thumbnail.
            } catch (_: OutOfMemoryError) {
            }
        }
    }

    private fun save(context: Context, uri: Uri, face: Box, out: File) {
        val resolver = context.contentResolver
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val decoder = resolver.openInputStream(uri)?.use { regionDecoder(it) } ?: return
        try {
            val turned = orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270
            val dw = if (turned) decoder.height else decoder.width
            val dh = if (turned) decoder.width else decoder.height
            // A square around the face with a little room, in the photo as it is shown.
            val side = 1.5f * max(face.width * dw, face.height * dh)
            val cx = (face.left + face.right) / 2f * dw
            val cy = (face.top + face.bottom) / 2f * dh
            val shown = Box(
                ((cx - side / 2f) / dw).coerceIn(0f, 1f), ((cy - side / 2f) / dh).coerceIn(0f, 1f),
                ((cx + side / 2f) / dw).coerceIn(0f, 1f), ((cy + side / 2f) / dh).coerceIn(0f, 1f),
            )
            val raw = toStored(shown, orientation)
            val rect = Rect(
                (raw.left * decoder.width).roundToInt(), (raw.top * decoder.height).roundToInt(),
                (raw.right * decoder.width).roundToInt(), (raw.bottom * decoder.height).roundToInt(),
            )
            if (rect.width() < 8 || rect.height() < 8) return
            var sample = 1
            while (min(rect.width(), rect.height()) / (sample * 2) >= SIDE) sample *= 2
            val region = decoder.decodeRegion(rect, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val upright = if (degrees != 0f) {
                Bitmap.createBitmap(region, 0, 0, region.width, region.height, Matrix().apply { postRotate(degrees) }, true)
                    .also { if (it !== region) region.recycle() }
            } else region
            val square = Bitmap.createScaledBitmap(upright, SIDE, SIDE, true)
            out.parentFile?.mkdirs()
            FileOutputStream(out).use { square.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (square !== upright) square.recycle()
            upright.recycle()
        } finally {
            decoder.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun regionDecoder(stream: InputStream): BitmapRegionDecoder? =
        if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(stream) else BitmapRegionDecoder.newInstance(stream, false)

    /**
     * A box in the photo as shown (after the camera's rotation) to the same box in the file as
     * stored. ROTATE_90 means the stored picture is shown turned 90 degrees clockwise.
     */
    fun toStored(shown: Box, orientation: Int): Box {
        fun point(u: Float, v: Float): Pair<Float, Float> = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> v to 1f - u
            ExifInterface.ORIENTATION_ROTATE_180 -> 1f - u to 1f - v
            ExifInterface.ORIENTATION_ROTATE_270 -> 1f - v to u
            else -> u to v
        }
        val (x1, y1) = point(shown.left, shown.top)
        val (x2, y2) = point(shown.right, shown.bottom)
        return Box(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
    }
}
