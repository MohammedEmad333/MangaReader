package com.mangareader.app

import java.io.File

/**
 * The reader checks disk images off the Compose rendering thread. File inspection
 * must finish before trying to decode or rotate a page.
 */
internal data class ReaderPageInspection(
    val file: File,
    val wideMode: ReaderWidePageMode,
    val usable: Boolean,
    val rotateWide: Boolean,
    val canRedownload: Boolean,
)

internal fun readerPageInspection(
    file: File,
    cacheRoot: File,
    wideMode: ReaderWidePageMode,
    bounds: (File) -> Pair<Int, Int>,
): ReaderPageInspection {
    val usable = isUsableReaderPage(file)
    val rotate = if (usable && wideMode == ReaderWidePageMode.ROTATE_RIGHT) {
        val (width, height) = bounds(file)
        shouldRotateWidePage(width, height, wideMode)
    } else {
        false
    }
    return ReaderPageInspection(
        file = file,
        wideMode = wideMode,
        usable = usable,
        rotateWide = rotate,
        canRedownload = isReaderCachePage(file, cacheRoot),
    )
}

/**
 * An async result may still be remembered while Compose switches page or
 * rotation mode. Only draw it when both keys match the current request.
 */
internal fun currentReaderPageInspection(
    inspection: ReaderPageInspection?,
    file: File?,
    wideMode: ReaderWidePageMode,
): ReaderPageInspection? =
    inspection?.takeIf { file != null && it.file == file && it.wideMode == wideMode }
