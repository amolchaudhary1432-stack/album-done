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
    /** Share of the photo covered by people (from the object detector), 0..1. */
    val peopleArea: Float,
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
    private val finder = FaceFinder.get(context)

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
            val surePeople = found.filter { d -> d.categories().any { it.categoryName() == "person" && it.score() >= 0.6f } }
                .map { toBox(it.boundingBox(), bitmap) }
            val phones = found.filter { d -> d.categories().any { it.categoryName() == "cell phone" } }
                .map { toBox(it.boundingBox(), bitmap) }

            val faces = findFaces(bitmap).map { measureFace(bitmap, gray, qw, qh, it) }
            val backs = SceneRules.foregroundBacks(surePeople, faces.map { it.reading.box })

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
                peopleArea = people.sumOf { it.area.toDouble() }.toFloat().coerceIn(0f, 1f),
            )
        } finally {
            bitmap.recycle()
        }
    }

    /** Landmarks of one face in whole-photo coordinates (0..1). */
    private class RawFace(val xs: FloatArray, val ys: FloatArray, val blend: Map<String, Float>, val matrix: FloatArray?)

    /** A face YuNet found, with the face mesh when it could be read. */
    private class Face(val found: YuNet.Found, val mesh: RawFace?)

    /**
     * YuNet finds every face (small, side-on, downcast); then each face is read from a close crop
     * by the face mesh model, which works best when the face fills its picture. Faces it cannot
     * read are kept: they still count as people and are still recognised.
     */
    private fun findFaces(bitmap: Bitmap): List<Face> {
        val found = finder.find(bitmap).sortedByDescending { it.box.width }.take(MAX_FACES)
        return found.map { f -> Face(f, if (f.box.width >= MIN_MESH_FACE) readCrop(bitmap, f) else null) }
    }

    /** The face mesh for [face], read from a square crop around it. */
    private fun readCrop(bitmap: Bitmap, face: YuNet.Found): RawFace? {
        val side = CROP_AROUND_FACE * max(face.box.width * bitmap.width, face.box.height * bitmap.height)
        val cx = (face.box.left + face.box.right) / 2f * bitmap.width
        val cy = (face.box.top + face.box.bottom) / 2f * bitmap.height
        val x0 = (cx - side / 2f).toInt().coerceIn(0, bitmap.width - 1)
        val y0 = (cy - side / 2f).toInt().coerceIn(0, bitmap.height - 1)
        val x1 = (cx + side / 2f).toInt().coerceIn(x0 + 1, bitmap.width)
        val y1 = (cy + side / 2f).toInt().coerceIn(y0 + 1, bitmap.height)
        if (x1 - x0 < 16 || y1 - y0 < 16) return null
        val crop = Bitmap.createBitmap(bitmap, x0, y0, x1 - x0, y1 - y0)
        val scale = CROP_SIDE / max(crop.width, crop.height).toFloat()
        val sized = Bitmap.createScaledBitmap(crop, max(1, (crop.width * scale).roundToInt()), max(1, (crop.height * scale).roundToInt()), true)
        val area = Box(x0.toFloat() / bitmap.width, y0.toFloat() / bitmap.height, x1.toFloat() / bitmap.width, y1.toFloat() / bitmap.height)
        val meshes = try { detect(sized, area) } finally {
            if (sized !== crop) sized.recycle()
            if (crop !== bitmap) crop.recycle()
        }
        // The crop may show a neighbour too; take the mesh centred inside this face.
        return meshes.firstOrNull { m ->
            val mx = m.xs.take(468).average().toFloat()
            val my = m.ys.take(468).average().toFloat()
            mx in face.box.left..face.box.right && my in face.box.top..face.box.bottom
        }
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

    private fun measureFace(bitmap: Bitmap, gray: FloatArray, qw: Int, qh: Int, face: Face): FaceAnalysis {
        val aspect = bitmap.width.toFloat() / bitmap.height
        val f = face.mesh
        val r = if (f != null) FaceMetrics.read(f.xs, f.ys, f.blend, f.matrix, aspect)
        else FaceMetrics.readFromPoints(face.found.box, face.found.points, aspect)
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

        // Skin patches: cheeks, forehead, chin, nose bridge (needs the face mesh).
        val occlusion = if (f != null) {
            val radius = max(2, (pw * 0.04f).roundToInt())
            val patches = FaceMetrics.SKIN_PATCHES.filter { it < f.xs.size }.map { i ->
                patchMean(bitmap, (f.xs[i] * bitmap.width).roundToInt(), (f.ys[i] * bitmap.height).roundToInt(), radius)
            }
            FaceMetrics.occlusion(patches.filterNotNull())
        } else 0f

        // Fingerprint for recognising the person; only for faces big enough to be reliable.
        // Small or strongly side-on faces give unreliable fingerprints that mix people up.
        val embedding = if (box.width >= MIN_RECOGNISE_FACE && r.cutOff < 0.2f && r.yaw <= 50f) {
            try {
                val points = if (f != null) FaceMetrics.alignmentPoints(f.xs, f.ys, bitmap.width, bitmap.height)
                else FloatArray(10) { k -> face.found.points[k] * (if (k % 2 == 0) bitmap.width else bitmap.height) }
                embedder.embed(bitmap, points)
            } catch (_: Exception) {
                null
            }
        } else null

        val sizeScore = (box.width / 0.12f).coerceIn(0f, 1f)
        val angleScore = if (r.yaw < 30f) 1f else if (r.yaw < 50f) 0.5f else 0.2f
        val eyesScore = when (r.eyes) {
            EyeState.CLOSED -> 0.7f
            EyeState.UNKNOWN -> 0.85f
            else -> 1f
        }
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
        /** Each face is read from a square this many times its size, scaled to [CROP_SIDE] pixels. */
        const val CROP_AROUND_FACE = 2.2f
        const val CROP_SIDE = 256f
        /** Faces at least this wide (share of the photo) get a fingerprint to recognise the person. */
        const val MIN_RECOGNISE_FACE = 0.04f
        /** Smaller faces are counted, but too small to read their eyes. */
        const val MIN_MESH_FACE = 0.015f

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
