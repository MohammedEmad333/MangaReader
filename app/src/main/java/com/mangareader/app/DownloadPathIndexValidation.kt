package com.mangareader.app

/**
 * Download paths are relative to the selected download root.
 * Never trust a damaged or edited on-disk index with absolute paths or traversal.
 */
internal fun isSafeStoredDownloadFolder(folder: String): Boolean =
    folder.isNotBlank() &&
        folder != "." && folder != ".." &&
        folder.none { it == '/' || it == '\\' || it.code < 32 }

/** Readable download locations use Source/Series/Chapter, exactly three levels. */
internal fun isSafeStoredChapterPath(path: String): Boolean {
    val segments = path.split('/')
    return segments.size == 3 && segments.all(::isSafeStoredDownloadFolder)
}
