package com.albumdone.app.analysis

import android.content.Context
import android.util.Log
import com.albumdone.app.plan.AlbumMind

/** Reads the album brain's learned numbers (assets/models/album_mind.bin) once per app run. */
object MindLoader {
    const val FILE = "models/album_mind.bin"

    fun ensure(context: Context) {
        if (AlbumMind.weights != null) return
        synchronized(this) {
            if (AlbumMind.weights != null) return
            try {
                AlbumMind.weights = AlbumMind.read(context.assets.open(FILE).use { it.readBytes() })
            } catch (e: Exception) {
                Log.w("AlbumDone", "album brain not loaded: ${e.message}")
            }
        }
    }
}
