package com.mangareader.app

import android.os.Environment
import java.io.File

/**
 * Looks for `.tachibk` files on external storage.
 *
 * A directory walk rather than a document picker because this app already holds
 * MANAGE_EXTERNAL_STORAGE (see the note on `StorageLocation`), so it can simply
 * read the file. Depth-capped and `Android/` is skipped, or this walks the whole
 * card looking at every app's private data.
 */
internal fun findBackupFiles(): List<File> {
    val root = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        ?: return emptyList()
    val found = mutableListOf<File>()

    fun walk(dir: File, depth: Int) {
        if (depth > 5 || found.size >= 40) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        children.forEach { f ->
            when {
                f.isDirectory && f.name != "Android" && !f.name.startsWith(".") ->
                    walk(f, depth + 1)
                f.isFile && (f.name.endsWith(".tachibk") || f.name.endsWith(".proto.gz")) ->
                    found.add(f)
                else -> Unit
            }
        }
    }
    walk(root, 0)
    return found.sortedByDescending { it.lastModified() }
}
