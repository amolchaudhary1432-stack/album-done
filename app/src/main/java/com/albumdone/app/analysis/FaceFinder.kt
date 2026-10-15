package com.albumdone.app.analysis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Finds every face in a photo with OpenCV Zoo's YuNet model (MIT licence), run on the phone by
 * ONNX Runtime. It finds small faces at the back of a group, side-on and downcast faces, which
 * the face mesh model (made for selfie-distance faces) misses. On a real wedding it found 361
 * faces where the mesh model alone found 67.
 *
 * One model is shared by all worker lanes (ONNX Runtime sessions are thread-safe).
 */
class FaceFinder private constructor(private val session: OrtSession) {

    private val env = OrtEnvironment.getEnvironment()
    private val inputName = session.inputNames.first()

    fun find(bitmap: Bitmap, longSide: Int = LONG_SIDE, threshold: Float = YuNet.THRESHOLD): List<YuNet.Found> {
        val scale = minOf(1f, longSide.toFloat() / max(bitmap.width, bitmap.height))
        val w = max(1, (bitmap.width * scale).roundToInt())
        val h = max(1, (bitmap.height * scale).roundToInt())
        val small = if (w != bitmap.width || h != bitmap.height) Bitmap.createScaledBitmap(bitmap, w, h, true) else bitmap
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()
        return YuNet.decode(runModel(pixels, w, h), w, h, threshold)
    }

    /** The model's raw outputs for one image, by name (cls_8, obj_8, bbox_8, kps_8, ...). */
    private fun runModel(pixels: IntArray, w: Int, h: Int): YuNet.Outputs {
        val pw = (w + 31) / 32 * 32
        val ph = (h + 31) / 32 * 32
        // Input: 1x3xHxW, blue/green/red planes, values 0..255, padded with black to a multiple of 32.
        val plane = pw * ph
        val data = FloatArray(3 * plane)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = pixels[y * w + x]
                val i = y * pw + x
                data[i] = (p and 0xFF).toFloat()
                data[plane + i] = ((p shr 8) and 0xFF).toFloat()
                data[2 * plane + i] = ((p shr 16) and 0xFF).toFloat()
            }
        }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, ph.toLong(), pw.toLong())).use { input ->
            session.run(mapOf(inputName to input)).use { result ->
                val out = HashMap<String, FloatArray>()
                for (name in OUTPUTS) {
                    val tensor = result.get(name).orElse(null) as? OnnxTensor ?: continue
                    val buffer = tensor.floatBuffer
                    out[name] = FloatArray(buffer.remaining()).also { buffer.get(it) }
                }
                return YuNet.Outputs(out, pw, ph)
            }
        }
    }

    companion object {
        const val MODEL = "models/face_detection_yunet_2026may.onnx"
        /** Faces are looked for at this size: small faces stay findable, and it stays quick. */
        const val LONG_SIDE = 960
        private val OUTPUTS = YuNet.STRIDES.flatMap { s -> listOf("cls_$s", "obj_$s", "bbox_$s", "kps_$s") }

        @Volatile private var instance: FaceFinder? = null

        fun get(context: Context): FaceFinder =
            instance ?: synchronized(this) {
                instance ?: run {
                    val bytes = context.assets.open(MODEL).use { it.readBytes() }
                    val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
                    FaceFinder(OrtEnvironment.getEnvironment().createSession(bytes, options))
                }.also { instance = it }
            }
    }
}
