package com.mangareader.app

import java.io.File

/**
 * Pages can be replaced by a retry without changing their File path. Snapshot
 * cheap file metadata so old Coil models and asynchronous inspections cannot
 * keep treating an overwritten page as the same decoded image.
 *
 * The retry generation covers two writes within the same timestamp resolution.
 * Image decoding and header inspection remain off the UI thread.
 */
internal data class ReaderImageFileRevision(
    val file: File,
    val length: Long,
    val lastModified: Long,
    val retryGeneration: Int,
) {
    fun cacheKey(variant: String, decodeAttempt: Int = 0): String =
        "${file.absolutePath}:$length:$lastModified:$retryGeneration:$variant:$decodeAttempt"
}

internal fun readerImageFileRevision(
    file: File?,
    retryGeneration: Int = 0,
): ReaderImageFileRevision? = file?.let {
    ReaderImageFileRevision(
        file = it,
        length = it.length(),
        lastModified = it.lastModified(),
        retryGeneration = retryGeneration,
    )
}
