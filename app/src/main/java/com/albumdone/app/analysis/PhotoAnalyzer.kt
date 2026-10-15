package com.albumdone.app.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.albumdone.app.plan.Box
import com.albumdone.app.plan.EyeState
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One face found and measured in a photo. */
data class FaceAnalysis(
    val reading: FaceMetrics.Reading,
    val sharpness: Double,
    val brightness: Double,
    val backlit: Boolean,
    val occlusion: Float,
    /** 0..1: how reliable this face is for recognising the person. */
    val quality: Float,
    val embedding: FloatArray?,
)

data class PhotoAnalysis(
    val takenAt: Long,
    val width: Int,
    val height: Int,
    val quality: ImageMetrics.Quality,
    val peakSharpness: Double,
    val tilt: Float,
    val hash: Long,
    val faces: List<FaceAnalysis>,
    val phones: List<Box>,
    val foregroundBacks: Int,
)

/**
 * Analyses one photo entirely on the phone.
 *
 * Memory rule: never decode the full-size photo. Each photo is decoded at about 1280px on its
 * long side, measured, turned into a small thumbnail, and released.
 * One analyzer is used per worker lane; it is not shared between threads.
 */
class PhotoAnalyzer(private val context: Context) : AutoCloseable {

    private val landmarker: FaceLandmarker = FaceLandmarker.createFromOptions(
        context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(FACE_MODEL).build())
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(MAX_FACES)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(true)
            .build()
    )

    private val objects: ObjectDetector = ObjectDetector.createFromOptions(
        context,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(OBJECT_MODEL).build())
            .setRunningMode(RunningMode.IMAGE)
            .setMaxResults(30)
            .setScoreThreshold(0.35f)
            .setCategoryAllowlist(listOf("person", "cell phone"))
            .build()
    )

    private val embedder = FaceEmbedder.get(context)

    fun analyze(uri: Uri, thumbFile: File): PhotoAnalysis {
        val resolver = context.contentResolver

        // Reading only the size returns no bitmap by design, so check the file opened instead.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val sizeStream = resolver.openInputStream(uri) ?: error("Could not open photo")
        sizeStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        val fullW = bounds.outWidth
        val fullH = bounds.outHeight
        if (fullW <= 0 || fullH <= 0) error("Not a readable photo")

        var orientation = ExifInterface.ORIENTATION_NORMAL
        var takenAt = 0L
        try {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                takenAt = parseExifDate(
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                )
            }
        } catch (_: Exception) {
            // Some files have no or broken EXIF data; analysis still works without it.
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(max(fullW, fullH), TARGET_LONG_SIDE)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
            ?: error("Could not decode photo")
        val bitmap = rotate(decoded, orientation)

        try {
            // Small greyscale copy for whole-photo measurements.
            val qw = QUALITY_WIDTH
            val qh = max(1, (bitmap.height.toFloat() * qw / bitmap.width).roundToInt())
            val gray = grayOf(bitmap, qw, qh)
            val quality = ImageMetrics.quality(gray, qw, qh)
            val peak = ImageMetrics.peakSharpness(gray, qw, qh)
            val tilt = ImageMetrics.tiltDegrees(gray, qw, qh) ?: 0f
            val hash = ImageMetrics.dHash(grayOf(bitmap, 9, 8))

            val image = BitmapImageBuilder(bitmap).build()
            val found = objects.detect(image).detections()
            val people = found.filter { d -> d.categories().any { it.categoryName() == "person" && it.score() >= 0.45f } }
                .map { toBox(it.boundingBox(), bitmap) }
            val phones = found.filter { d -> d.categories().any { it.categoryName() == "cell phone" } }
                .map { toBox(it.boundingBox(), bitmap) }

            val raw = findFaces(bitmap, people)
            val faces = raw.map { measureFace(bitmap, gray, qw, qh, it) }
            val backs = SceneRules.foregroundBacks(people, faces.map { it.reading.box })

            writeThumbnail(bitmap, thumbFile)
            val rotatedSides = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270
            return PhotoAnalysis(
                takenAt = takenAt,
                width = if (rotatedSides) fullH else fullW,
                height = if (rotatedSides) fullW else fullH,
                quality = quality,
                peakSharpness = peak,
                tilt = tilt,
                hash = hash,
                faces = faces,
                phones = phones,
                foregroundBacks = backs,
            )
        } finally {
            bitmap.recycle()
        }
    }

    /** Landmarks of one face in whole-photo coordinates (0..1). */
    private class RawFace(val xs: FloatArray, val ys: FloatArray, val blend: Map<String, Float>, val matrix: FloatArray?)

    /**
     * Faces in the whole photo, then a closer look at people the first look missed (small faces
     * at the back of a group photo).
     */
    private fun findFaces(bitmap: Bitmap, people: List<Box>): List<RawFace> {
        val faces = ArrayList(detect(bitmap, Box(0f, 0f, 1f, 1f)))
        val boxes = faces.map { FaceMetrics.box(it.xs, it.ys) }.toMutableList()
        val missing = SceneRules.peopleWithoutFaces(people, boxes)
        for (area in SceneRules.headCrops(missing)) {
            val x = (area.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val y = (area.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val w = (area.width * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
            val h = (area.height * bitmap.height).toInt().coerceIn(1, bitmap.height - y)
            if (w < 16 || h < 16) continue
            val crop = Bitmap.createBitmap(bitmap, x, y, w, h)
            // Enlarge small areas so faces reach a size the detector handles well.
            val scale = max(1f, CROP_SIDE / max(w, h).toFloat())
            val big = if (scale > 1f) Bitmap.createScaledBitmap(crop, (w * scale).roundToInt(), (h * scale).roundToInt(), true) else crop
            val area01 = Box(x.toFloat() / bitmap.width, y.toFloat() / bitmap.height, (x + w).toFloat() / bitmap.width, (y + h).toFloat() / bitmap.height)
            for (f in detect(big, area01)) {
                val b = FaceMetrics.box(f.xs, f.ys)
                if (SceneRules.isDuplicate(b, boxes)) continue
                faces += f
                boxes += b
            }
            if (big !== crop) big.recycle()
            if (crop !== bitmap) crop.recycle()
        }
        return faces
    }

    /** Runs the face landmarker on [image], which shows [area] of the photo. */
    private fun detect(image: Bitmap, area: Box): List<RawFace> {
        val result = landmarker.detect(BitmapImageBuilder(image).build())
        val landmarks = result.faceLandmarks()
        val blendshapes = result.faceBlendshapes().orElse(null)
        val matrices = result.facialTransformationMatrixes().orElse(null)
        return landmarks.indices.map { i ->
            val points = landmarks[i]
            val xs = FloatArray(points.size) { area.left + points[it].x() * area.width }
            val ys = FloatArray(points.size) { area.top + points[it].y() * area.height }
            val blend = blendshapes?.getOrNull(i)?.associate { it.categoryName() to it.score() } ?: emptyMap()
            RawFace(xs, ys, blend, matrices?.getOrNull(i))
        }
    }

    private fun measureFace(bitmap: Bitmap, gray: FloatArray, qw: Int, qh: Int, f: RawFace): FaceAnalysis {
        val aspect = bitmap.width.toFloat() / bitmap.height
        val r = FaceMetrics.read(f.xs, f.ys, f.blend, f.matrix, aspect)
        val box = r.box
        val px = (box.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val py = (box.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val pw = (box.width * bitmap.width).toInt().coerceIn(1, bitmap.width - px)
        val ph = (box.height * bitmap.height).toInt().coerceIn(1, bitmap.height - py)

        // Sharpness and light on the face only (backgrounds are often blurred on purpose).
        val side = min(FACE_SIDE, max(pw, ph))
        val fw = max(3, (pw.toFloat() * side / max(pw, ph)).roundToInt())
        val fh = max(3, (ph.toFloat() * side / max(pw, ph)).roundToInt())
        val crop = Bitmap.createBitmap(bitmap, px, py, pw, ph)
        val faceGray = grayOf(crop, fw, fh)
        if (crop !== bitmap) crop.recycle()
        val sharpness = ImageMetrics.quality(faceGray, fw, fh).sharpness
        val inner = Box(0.2f, 0.2f, 0.8f, 0.8f)
        val brightness = ImageMetrics.regionMean(faceGray, fw, fh, inner)
        val around = Box(box.left - box.width, box.top - box.height * 0.5f, box.right + box.width, box.bottom + box.height * 0.5f)
        val surround = ImageMetrics.regionMean(gray, qw, qh, around, exclude = box)
        val backlit = ImageMetrics.isBacklit(brightness, surround)

        // Skin patches: cheeks, forehead, chin, nose bridge.
        val radius = max(2, (pw * 0.04f).roundToInt())
        val patches = FaceMetrics.SKIN_PATCHES.filter { it < f.xs.size }.map { i ->
            patchMean(bitmap, (f.xs[i] * bitmap.width).roundToInt(), (f.ys[i] * bitmap.height).roundToInt(), radius)
        }
        val occlusion = FaceMetrics.occlusion(patches.filterNotNull())

        // Fingerprint for recognising the person; only for faces big enough to be reliable.
        val embedding = if (box.width >= 0.03f && r.cutOff < 0.2f) {
            try {
                embedder.embed(bitmap, FaceMetrics.alignmentPoints(f.xs, f.ys, bitmap.width, bitmap.height))
            } catch (_: Exception) {
                null
            }
        } else null

        val sizeScore = (box.width / 0.12f).coerceIn(0f, 1f)
        val angleScore = if (r.yaw < 30f) 1f else if (r.yaw < 50f) 0.5f else 0.2f
        val eyesScore = if (r.eyes == EyeState.CLOSED) 0.7f else 1f
        val quality = sizeScore * angleScore * eyesScore * (1f - 0.5f * occlusion)

        return FaceAnalysis(r, sharpness, brightness, backlit, occlusion, quality, embedding)
    }

    private fun patchMean(bitmap: Bitmap, cx: Int, cy: Int, radius: Int): FloatArray? {
        val x0 = cx - radius; val y0 = cy - radius
        if (x0 < 0 || y0 < 0 || cx + radius >= bitmap.width || cy + radius >= bitmap.height) return null
        val size = 2 * radius + 1
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, x0, y0, size, size)
        var r = 0f; var g = 0f; var b = 0f
        for (p in pixels) { r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF }
        val n = pixels.size.toFloat()
        return floatArrayOf(r / n, g / n, b / n)
    }

    private fun toBox(r: android.graphics.RectF, bitmap: Bitmap) =
        Box(r.left / bitmap.width, r.top / bitmap.height, r.right / bitmap.width, r.bottom / bitmap.height)

    private fun grayOf(bitmap: Bitmap, w: Int, h: Int): FloatArray {
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()
        return ImageMetrics.luma(pixels)
    }

    private fun writeThumbnail(bitmap: Bitmap, file: File) {
        val longSide = max(bitmap.width, bitmap.height).toFloat()
        val scale = THUMB_LONG_SIDE / longSide
        val thumb = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * scale).roundToInt()), max(1, (bitmap.height * scale).roundToInt()), true)
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
        landmarker.close()
        objects.close()
    }

    companion object {
        const val FACE_MODEL = "models/face_landmarker.task"
        const val OBJECT_MODEL = "models/efficientdet_lite0.tflite"
        const val TARGET_LONG_SIDE = 1280
        const val QUALITY_WIDTH = 256
        const val THUMB_LONG_SIDE = 480f
        const val MAX_FACES = 20
        const val FACE_SIDE = 128
        const val CROP_SIDE = 640f

        /** Largest power-of-two shrink that still keeps the long side at or above [target]. */
        fun sampleSizeFor(longSide: Int, target: Int): Int {
            var sample = 1
            while (longSide / (sample * 2) >= target) sample *= 2
            return sample
        }

        private val exifFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US) }

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
