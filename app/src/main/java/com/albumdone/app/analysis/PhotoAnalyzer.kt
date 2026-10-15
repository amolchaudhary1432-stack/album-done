package com.albumdone.app.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

data class PhotoAnalysis(
    val takenAt: Long,
    val width: Int,
    val height: Int,
    val quality: ImageMetrics.Quality,
    val hash: Long,
    val faceCount: Int,
    val smile: Float,
    val eyesClosed: Boolean,
)

/**
 * Analyses one photo entirely on the phone.
 *
 * Memory rule: never decode the full-size photo. Each photo is decoded at about 1280px on its
 * long side (enough for face detection), measured, turned into a small thumbnail, and released.
 * One analyzer is used per worker lane; it is not shared between threads.
 */
class PhotoAnalyzer(private val context: Context) : AutoCloseable {

    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.04f)
            .build()
    )

    fun analyze(uri: Uri, thumbFile: File): PhotoAnalysis {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("Could not open photo")
        val fullW = bounds.outWidth
        val fullH = bounds.outHeight
        if (fullW <= 0 || fullH <= 0) error("Not a readable photo")

        var orientation = ExifInterface.ORIENTATION_NORMAL
        var takenAt = 0L
        try {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                )
                takenAt = parseExifDate(
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                )
            }
        } catch (_: Exception) {
            // Some files have no or broken EXIF data; analysis still works without it.
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(max(fullW, fullH), TARGET_LONG_SIDE)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: error("Could not decode photo")
        val bitmap = rotate(decoded, orientation)

        try {
            val quality = measureQuality(bitmap)
            val hash = measureHash(bitmap)
            val faces = detectFaces(bitmap)
            writeThumbnail(bitmap, thumbFile)
            val rotatedSides = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270
            return PhotoAnalysis(
                takenAt = takenAt,
                width = if (rotatedSides) fullH else fullW,
                height = if (rotatedSides) fullW else fullH,
                quality = quality,
                hash = hash,
                faceCount = faces.count,
                smile = faces.smile,
                eyesClosed = faces.eyesClosed,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun measureQuality(bitmap: Bitmap): ImageMetrics.Quality {
        val w = QUALITY_WIDTH
        val h = max(1, (bitmap.height.toFloat() * w / bitmap.width).roundToInt())
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()
        return ImageMetrics.quality(ImageMetrics.luma(pixels), w, h)
    }

    private fun measureHash(bitmap: Bitmap): Long {
        val tiny = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        val pixels = IntArray(72)
        tiny.getPixels(pixels, 0, 9, 0, 0, 9, 8)
        if (tiny !== bitmap) tiny.recycle()
        return ImageMetrics.dHash(ImageMetrics.luma(pixels))
    }

    private data class FaceResult(val count: Int, val smile: Float, val eyesClosed: Boolean)

    private fun detectFaces(bitmap: Bitmap): FaceResult {
        val faces = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
        // Only faces big enough to matter in an album photo count towards smile and eyes.
        val minWidth = bitmap.width * 0.06f
        val clear = faces.filter { it.boundingBox.width() >= minWidth }
        var bestSmile = -1f
        var anyClosed = false
        for (face in clear) {
            face.smilingProbability?.let { if (it > bestSmile) bestSmile = it }
            val left = face.leftEyeOpenProbability
            val right = face.rightEyeOpenProbability
            if (left != null && right != null && (left + right) / 2f < EYES_CLOSED_BELOW) {
                anyClosed = true
            }
        }
        return FaceResult(faces.size, bestSmile, anyClosed)
    }

    private fun writeThumbnail(bitmap: Bitmap, file: File) {
        val longSide = max(bitmap.width, bitmap.height).toFloat()
        val scale = THUMB_LONG_SIDE / longSide
        val thumb = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * scale).roundToInt()),
                max(1, (bitmap.height * scale).roundToInt()),
                true,
            )
        } else bitmap
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { thumb.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        if (thumb !== bitmap) thumb.recycle()
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    override fun close() {
        detector.close()
    }

    companion object {
        const val TARGET_LONG_SIDE = 1280
        const val QUALITY_WIDTH = 256
        const val THUMB_LONG_SIDE = 360f
        const val EYES_CLOSED_BELOW = 0.3f

        /** Largest power-of-two shrink that still keeps the long side at or above [target]. */
        fun sampleSizeFor(longSide: Int, target: Int): Int {
            var sample = 1
            while (longSide / (sample * 2) >= target) sample *= 2
            return sample
        }

        private val exifFormat = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        }

        fun parseExifDate(value: String?): Long {
            if (value.isNullOrBlank()) return 0L
            return try {
                exifFormat.get()!!.parse(value.trim())?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }
}
