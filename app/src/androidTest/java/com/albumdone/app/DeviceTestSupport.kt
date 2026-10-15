package com.albumdone.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream

/** Helpers shared by the on-phone tests. */
object DeviceTestSupport {

    val app get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val tests get() = InstrumentationRegistry.getInstrumentation().context

    /** Copies a test photo out of the test package so the app can open it like a real file. */
    fun photo(name: String, folder: String = "", asName: String = name): Uri {
        val dir = File(app.cacheDir, "test-wedding/$folder")
        dir.mkdirs()
        val file = File(dir, asName)
        tests.assets.open("faces/$name").use { input -> FileOutputStream(file).use { input.copyTo(it) } }
        return Uri.fromFile(file)
    }

    /** Saves a screenshot where the build workflow collects it. */
    fun saveScreenshot(name: String, bitmap: Bitmap) {
        val dir = File(app.getExternalFilesDir(null), "screens")
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
