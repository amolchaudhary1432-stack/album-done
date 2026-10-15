package com.albumdone.app.analysis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Turns a face into a 128-number fingerprint with OpenCV Zoo's SFace model (Apache 2.0),
 * run on the phone by ONNX Runtime. Two faces of the same person give similar fingerprints.
 *
 * One model is shared by all worker lanes: ONNX Runtime sessions are safe to use from
 * several threads, and the model is 38 MB, so loading it once saves memory.
 */
class FaceEmbedder private constructor(private val session: OrtSession) {

    private val env = OrtEnvironment.getEnvironment()
    private val inputName = session.inputNames.first()

    /**
     * @param bitmap the decoded photo
     * @param points the 5 alignment points in [bitmap] pixels (see [FaceMetrics.alignmentPoints])
     */
    fun embed(bitmap: Bitmap, points: FloatArray): FloatArray {
        val aligned = align(bitmap, points)
        val pixels = IntArray(SIZE * SIZE)
        aligned.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        aligned.recycle()
        // SFace input: 1x3x112x112, red/green/blue planes, values 0..255 (as OpenCV feeds it).
        val data = FloatArray(3 * SIZE * SIZE)
        val plane = SIZE * SIZE
        for (i in 0 until plane) {
            val p = pixels[i]
            data[i] = ((p shr 16) and 0xFF).toFloat()
            data[plane + i] = ((p shr 8) and 0xFF).toFloat()
            data[2 * plane + i] = (p and 0xFF).toFloat()
        }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { input ->
            session.run(mapOf(inputName to input)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = (result.get(0).value as Array<FloatArray>)[0]
                return FaceMetrics.normalize(out.copyOf())
            }
        }
    }

    /** Turns, scales and crops the face so its eyes, nose and mouth sit where SFace expects. */
    private fun align(bitmap: Bitmap, points: FloatArray): Bitmap {
        val t = FaceMetrics.similarity(points, FaceMetrics.SFACE_TEMPLATE)
        val m = Matrix()
        m.setValues(floatArrayOf(t[0], -t[1], t[2], t[1], t[0], t[3], 0f, 0f, 1f))
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(bitmap, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    companion object {
        const val SIZE = 112
        const val MODEL = "models/face_recognition_sface_2021dec.onnx"

        @Volatile private var instance: FaceEmbedder? = null

        fun get(context: Context): FaceEmbedder =
            instance ?: synchronized(this) {
                instance ?: run {
                    val bytes = context.assets.open(MODEL).use { it.readBytes() }
                    val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
                    FaceEmbedder(OrtEnvironment.getEnvironment().createSession(bytes, options))
                }.also { instance = it }
            }

        fun toBytes(v: FloatArray): ByteArray {
            val b = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            v.forEach { b.putFloat(it) }
            return b.array()
        }

        fun fromBytes(bytes: ByteArray): FloatArray {
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(bytes.size / 4) { b.getFloat() }
        }
    }
}
