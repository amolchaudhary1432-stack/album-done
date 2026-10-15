package com.albumdone.app.analysis

import com.albumdone.app.plan.Box
import kotlin.math.exp
import kotlin.math.sqrt

/** Reading YuNet's outputs (pure Kotlin, so it is unit-tested on the computer). */
object YuNet {
    const val THRESHOLD = 0.6f
    const val NMS_OVERLAP = 0.3f
    val STRIDES = intArrayOf(8, 16, 32)

    /** The model's raw outputs by name (cls_8, obj_8, bbox_8, kps_8, ...) and the padded input size. */
    class Outputs(val values: Map<String, FloatArray>, val paddedWidth: Int, val paddedHeight: Int)

    /**
     * One face. [box] and [points] are 0..1 of the photo. [points] holds 5 x/y pairs in the order
     * SFace expects: eye on the image's left, eye on the image's right, nose tip, mouth corner on
     * the image's left, mouth corner on the image's right.
     */
    class Found(val box: Box, val points: FloatArray, val score: Float)

    /**
     * Turns the model's grid outputs into faces (as OpenCV's FaceDetectorYN does): for each
     * cell of each stride, score = sqrt(class x objectness), box from the cell plus offsets,
     * then overlapping boxes are merged. [w] x [h] is the image size before padding.
     */
    fun decode(outputs: Outputs, w: Int, h: Int, threshold: Float = THRESHOLD): List<Found> {
        val found = ArrayList<Found>()
        for (s in STRIDES) {
            val cls = outputs.values["cls_$s"] ?: continue
            val obj = outputs.values["obj_$s"] ?: continue
            val bbox = outputs.values["bbox_$s"] ?: continue
            val kps = outputs.values["kps_$s"] ?: continue
            val cols = outputs.paddedWidth / s
            for (i in cls.indices) {
                val score = sqrt(cls[i].coerceIn(0f, 1f) * obj[i].coerceIn(0f, 1f))
                if (score < threshold) continue
                val r = i / cols
                val c = i % cols
                val cx = (c + bbox[4 * i]) * s
                val cy = (r + bbox[4 * i + 1]) * s
                val bw = exp(bbox[4 * i + 2]) * s
                val bh = exp(bbox[4 * i + 3]) * s
                val box = Box((cx - bw / 2) / w, (cy - bh / 2) / h, (cx + bw / 2) / w, (cy + bh / 2) / h)
                val points = FloatArray(10) { k ->
                    if (k % 2 == 0) (kps[10 * i + k] + c) * s / w else (kps[10 * i + k] + r) * s / h
                }
                found += Found(box, points, score)
            }
        }
        return suppress(found)
    }

    /** Keeps the best of overlapping detections of the same face. */
    fun suppress(faces: List<Found>, overlap: Float = NMS_OVERLAP): List<Found> {
        val kept = ArrayList<Found>()
        for (f in faces.sortedByDescending { it.score }) {
            if (kept.none { iou(it.box, f.box) > overlap }) kept += f
        }
        return kept
    }

    fun iou(a: Box, b: Box): Float {
        val inter = a.intersection(b)
        val union = a.area + b.area - inter
        return if (union > 0f) inter / union else 0f
    }
}
