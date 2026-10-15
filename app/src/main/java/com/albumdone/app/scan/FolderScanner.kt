package com.albumdone.app.scan

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** A photo file found in the chosen folder. */
data class FoundPhoto(
    val docUri: Uri,
    val name: String,
    /** Path of sub-folders below the chosen folder, "" for the top level. */
    val folder: String,
    val size: Long,
    val lastModified: Long,
)

/**
 * Lists every photo in a folder the user picked, including sub-folders.
 *
 * Uses the folder access the user granted (no gallery-wide permission) and queries the
 * system directly, which is much faster than walking files one by one for large folders.
 */
object FolderScanner {

    private val photoTypes = setOf(
        "image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif",
    )
    private val photoExtensions = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")

    private val columns = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    fun scan(context: Context, treeUri: Uri, onFound: (Int) -> Unit = {}): List<FoundPhoto> {
        val results = mutableListOf<FoundPhoto>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        // Iterative walk to avoid deep recursion on unusual folder structures.
        val stack = ArrayDeque<Pair<String, String>>()
        stack.addLast(rootId to "")
        val resolver = context.contentResolver

        while (stack.isNotEmpty()) {
            val (docId, path) = stack.removeLast()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            resolver.query(childrenUri, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (name.startsWith(".")) continue
                    val mime = c.getString(2) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val childPath = if (path.isEmpty()) name else "$path/$name"
                        stack.addLast(id to childPath)
                        continue
                    }
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (mime.lowercase() !in photoTypes && ext !in photoExtensions) continue
                    results += FoundPhoto(
                        docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                        name = name,
                        folder = path,
                        size = if (c.isNull(3)) 0 else c.getLong(3),
                        lastModified = if (c.isNull(4)) 0 else c.getLong(4),
                    )
                    if (results.size % 50 == 0) onFound(results.size)
                }
            }
        }
        onFound(results.size)
        return results
    }
}
