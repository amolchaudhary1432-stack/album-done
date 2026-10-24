package com.albumdone.app.scan

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/**
 * The chosen photos as a folder for the photographer: a new folder in the place the couple picks
 * (Pictures, a pendrive), with the photographer's event sub-folders, each photo copied byte for
 * byte under its own name. Photographers want the photos themselves; finding names from a list is
 * work for them. Nothing is changed in the original folder, and nothing leaves the phone.
 */
object SelectionExport {

    /** Ends the new folder's name; the folder scanner skips such folders. */
    const val SUFFIX = " - album photos"

    /** One photo to copy: where it is, and where it goes (the photographer's sub-folder, or none). */
    data class Item(val source: Uri, val name: String, val folder: String)

    data class Result(val folderName: String, val saved: Int, val failed: Int)

    /** Pictures on the phone's own storage, where the folder picker opens. */
    val PICTURES: Uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Pictures")

    fun folderName(albumName: String, existing: Set<String>): String {
        val base = albumName.trim().ifEmpty { "Our album" }.replace(Regex("[\\\\/:*?\"<>|]"), " ") + SUFFIX
        if (base !in existing) return base
        var n = 2
        while ("$base ($n)" in existing) n++
        return "$base ($n)"
    }

    fun export(context: Context, destTree: Uri, albumName: String, items: List<Item>, onProgress: (Int) -> Unit): Result {
        val dest = DocumentFile.fromTreeUri(context, destTree) ?: error("Could not open the folder")
        val existing = dest.listFiles().mapNotNull { it.name }.toSet()
        val name = folderName(albumName, existing)
        val root = dest.createDirectory(name) ?: error("Could not make a folder here")
        val folders = HashMap<String, DocumentFile>()
        fun folderFor(path: String): DocumentFile {
            if (path.isEmpty()) return root
            return folders.getOrPut(path) {
                val parent = folderFor(path.substringBeforeLast('/', ""))
                val leaf = path.substringAfterLast('/')
                parent.findFile(leaf)?.takeIf { it.isDirectory } ?: parent.createDirectory(leaf) ?: error("Could not make $leaf")
            }
        }
        val resolver = context.contentResolver
        var saved = 0
        var failed = 0
        for ((i, item) in items.withIndex()) {
            try {
                val mime = resolver.getType(item.source) ?: "image/jpeg"
                val file = folderFor(item.folder).createFile(mime, item.name) ?: error("Could not make ${item.name}")
                resolver.openInputStream(item.source).use { input ->
                    resolver.openOutputStream(file.uri).use { output ->
                        requireNotNull(input).copyTo(requireNotNull(output), bufferSize = 256 * 1024)
                    }
                }
                saved++
            } catch (e: Exception) {
                failed++
            }
            onProgress(i + 1)
        }
        return Result(name, saved, failed)
    }
}
