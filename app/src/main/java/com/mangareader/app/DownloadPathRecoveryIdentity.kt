package com.mangareader.app

/**
 * A folder name may not identify a unique extension after corrupt imports or
 * interrupted index repairs. Prefer no recovery over assigning the wrong owner.
 */
internal fun uniqueDownloadFolderOwner(
    owners: Map<String, String>,
    folder: String,
    keyPrefix: String? = null,
): String? {
    if (folder.isBlank()) return null
    return owners.entries.singleOrNull { (key, mappedFolder) ->
        key.isNotBlank() && mappedFolder == folder &&
            (keyPrefix == null || key.startsWith(keyPrefix))
    }?.key
}

/**
 * A duplicated .chapterid marker in two directories is ambiguous. Reusing
 * the last file returned by listFiles() could surface an unrelated chapter.
 */
internal fun uniqueScannedChapterPaths(
    observed: List<Pair<String, String>>,
): Map<String, String> = observed.asSequence()
    .filter { (chapterId, path) ->
        chapterId.isNotBlank() && isSafeStoredChapterPath(path)
    }
    .groupBy { it.first }
    .mapNotNull { (chapterId, hits) ->
        val distinctPaths = hits.map { it.second }.distinct()
        distinctPaths.singleOrNull()?.let { chapterId to it }
    }
    .toMap()
