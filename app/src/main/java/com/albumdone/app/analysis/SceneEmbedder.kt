package com.albumdone.app.analysis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * What a photo shows, as 768 numbers: Google's SigLIP 2 image model (B/32, 256 px, Apache 2.0, int8
 * ONNX from onnx-community), run on the phone by ONNX Runtime. The album brain ([com.albumdone.app.plan.AlbumMind])
 * reads the whole folder through these numbers: which photos look alike (beats, near-copies), what
 * a moment shows, which frame people would keep.
 *
 * One session is shared by all worker lanes (ONNX Runtime sessions are thread-safe). The model is
 * 96 MB, so it is copied once from the app to its own files and opened from there rather than held
 * in the app's memory.
 */
class SceneEmbedder private constructor(private val session: OrtSession) {

    private val env = OrtEnvironment.getEnvironment()

    /** The photo squeezed to 256 x 256, as tools/reasoner/embed.py does; normalised 768 numbers. */
    fun embed(bitmap: Bitmap): FloatArray {
        val small = shrink(bitmap)
        val pixels = IntArray(SIZE * SIZE)
        small.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        if (small !== bitmap) small.recycle()
        val plane = SIZE * SIZE
        val data = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val p = pixels[i]
            data[i] = ((p shr 16) and 0xFF) / 127.5f - 1f
            data[plane + i] = ((p shr 8) and 0xFF) / 127.5f - 1f
            data[2 * plane + i] = (p and 0xFF) / 127.5f - 1f
        }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { input ->
            session.run(mapOf(INPUT to input), setOf(OUTPUT)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = (result.get(0).value as Array<FloatArray>)[0]
                return FaceMetrics.normalize(out.copyOf())
            }
        }
    }

    /** Halves the photo while it is over twice the size, then one smooth step to 256: close to a proper resample. */
    private fun shrink(bitmap: Bitmap): Bitmap {
        var b = bitmap
        while (max(b.width, b.height) > 2 * SIZE && b.width >= 2 * SIZE && b.height >= 2 * SIZE) {
            val half = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
            if (b !== bitmap) b.recycle()
            b = half
        }
        val out = Bitmap.createScaledBitmap(b, SIZE, SIZE, true)
        if (b !== bitmap && b !== out) b.recycle()
        return out
    }

    companion object {
        const val SIZE = 256
        const val MODEL = "models/siglip2_vision_int8.onnx"
        private const val INPUT = "pixel_values"
        private const val OUTPUT = "pooler_output"

        @Volatile private var instance: SceneEmbedder? = null

        /** Frees the model once a folder is checked (it is opened again for the next one). */
        fun release() = synchronized(this) {
            try { instance?.session?.close() } catch (_: Exception) {}
            instance = null
        }

        /** Null when the model is not in this build (the album then uses the older rules). */
        fun get(context: Context): SceneEmbedder? =
            instance ?: synchronized(this) {
                instance ?: try {
                    val file = File(context.filesDir, "siglip2_vision_int8.onnx")
                    val size = context.assets.openFd(MODEL).use { it.length }
                    if (!file.exists() || file.length() != size) {
                        val part = File(context.filesDir, "siglip2_vision_int8.onnx.part")
                        context.assets.open(MODEL).use { input -> part.outputStream().use { input.copyTo(it, 1 shl 20) } }
                        part.renameTo(file)
                    }
                    // Memory: on a Galaxy A15 the default session took the app from about 550 MB to 785 MB
                    // while checking. No arena (memory is given back after each photo) and no second,
                    // pre-packed copy of the weights.
                    val options = OrtSession.SessionOptions().apply {
                        setIntraOpNumThreads(2)
                        setCPUArenaAllocator(false)
                        setMemoryPatternOptimization(false)
                        addConfigEntry("session.disable_prepacking", "1")
                    }
                    SceneEmbedder(OrtEnvironment.getEnvironment().createSession(file.path, options))
                } catch (e: Exception) {
                    android.util.Log.w("SceneEmbedder", "no scene model: ${e.message}")
                    null
                }?.also { instance = it }
            }
    }
}
