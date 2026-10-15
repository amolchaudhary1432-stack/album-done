package com.albumdone.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.albumdone.app.plan.AlbumAnalysis
import com.albumdone.app.plan.Shortlist
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

    /**
     * Presses the phone's Back button through the system, the way a person does. (UiAutomator is
     * not used: its accessibility scanning interferes with the app's screens.)
     */
    fun pressBack() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_BACK")
            .use { pfd -> java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() } }
        Thread.sleep(500)
    }

    /** Saves a screenshot where the build workflow collects it. */
    fun saveScreenshot(name: String, bitmap: Bitmap) {
        val dir = File(app.filesDir, "screens")
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Copies a file from the test package's assets into the app's cache, as a phone folder would hold it. */
    fun copyAsset(path: String, folder: String, asName: String): Uri {
        val dir = File(app.cacheDir, folder)
        dir.mkdirs()
        val file = File(dir, asName)
        tests.assets.open(path).use { input -> FileOutputStream(file).use { input.copyTo(it) } }
        return Uri.fromFile(file)
    }

    fun testAssets(path: String): List<String> = tests.assets.list(path)?.toList().orEmpty()

    /** Every decision the app made about every photo, for Claude to review after the run. */
    fun writeReport(analysis: AlbumAnalysis, fileName: String, list: Shortlist? = null, header: String = "") {
        val picked = list?.ids?.toSet().orEmpty()
        val text = buildString {
            if (header.isNotEmpty()) appendLine(header)
            appendLine("Couple: bride=${analysis.tags.brideId} groom=${analysis.tags.groomId}")
            appendLine("Tags: " + analysis.tags.tags.values.joinToString { "${it.id}:${it.priority}/${it.side}${if (it.elder) "/elder" else ""}" })
            appendLine("People photo counts: " + analysis.personPhotoCount.entries.sortedByDescending { it.value }.take(25).joinToString { "${it.key}=${it.value}" })
            appendLine("Events: " + analysis.eventStats.joinToString { "${it.name} ${it.usable}/${it.total} ${it.kind}" })
            appendLine("Key moments: " + analysis.moments.filter { it.key }.joinToString { "${it.event}#${it.id}(${it.photoIds.size})" })
            if (list != null) {
                appendLine()
                appendLine("ALBUM: ${list.ids.size} photos, cover=${list.coverId}")
                for (id in list.ids) {
                    val a = analysis.byId.getValue(id)
                    appendLine("  ${a.photo.folder}/${a.photo.name}: " + list.reasons[id].orEmpty().joinToString(" | "))
                }
                appendLine("Coverage: " + list.coverage.joinToString { "${it.personId}:${it.picked}/${it.target}${it.warning?.let { w -> " ($w)" } ?: ""}" })
            }
            for (p in analysis.photos) {
                val ph = p.photo
                appendLine()
                appendLine("${ph.folder}/${ph.name} [${p.event}] ${ph.width}x${ph.height} ${if (p.id in picked) "PICKED " else ""}shot=${p.shot} usable=${p.usable} quality=${"%.3f".format(p.quality)} moment=${p.momentId} repeat=${p.repeatGroup} order=${p.order}")
                appendLine("  issues=${p.issues} people=${p.people} ritual=${p.ritual} emotional=${p.emotional} tilt=${ph.tilt} backs=${ph.foregroundBacks} phones=${ph.phones.size} taken=${ph.takenAt}")
                appendLine("  sharp=${"%.0f".format(ph.sharpness)} peak=${"%.0f".format(ph.peakSharpness)} bright=${"%.0f".format(ph.brightness)} dark=${"%.2f".format(ph.darkFraction)} blown=${"%.2f".format(ph.brightFraction)}")
                for (f in ph.faces) {
                    appendLine("  face person=${f.personId} box=${"%.2f,%.2f,%.2f,%.2f".format(f.box.left, f.box.top, f.box.right, f.box.bottom)} " +
                        "eyes=${f.eyes} expr=${f.expression} smile=${"%.2f".format(f.smile)} looking=${f.lookingAtCamera} dir=${"%.2f".format(f.directionX)} " +
                        "sharp=${"%.0f".format(f.sharpness)} bright=${"%.0f".format(f.brightness)} backlit=${f.backlit} occl=${"%.2f".format(f.occlusion)} cut=${"%.2f".format(f.cutOff)}")
                }
            }
        }
        val dir = File(app.filesDir, "screens")
        dir.mkdirs()
        File(dir, fileName).writeText(text)
    }
}
