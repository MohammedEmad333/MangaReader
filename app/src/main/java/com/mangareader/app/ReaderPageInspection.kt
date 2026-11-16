package com.mangareader.app

import java.io.File

/**
 * The reader checks disk images off the Compose rendering thread. File inspection
 * must finish before trying to decode or rotate a page.
 */
internal data class ReaderPageInspection(
    val file: File,
    val wideMode: ReaderWidePageMode,
    val revision: ReaderImageFileRevision,
    val usable: Boolean,
    val rotateWide: Boolean,
    val canRedownload: Boolean,
    val splitWide: Boolean = false,
)

internal fun readerPageInspection(
    file: File,
    cacheRoot: File,
    wideMode: ReaderWidePageMode,
    bounds: (File) -> Pair<Int, Int>,
    revision: ReaderImageFileRevision = requireNotNull(readerImageFileRevision(file)),
): ReaderPageInspection {
    val usable = isUsableReaderPage(file)
    val needsBounds = usable && (wideMode == ReaderWidePageMode.ROTATE_RIGHT ||
        wideMode == ReaderWidePageMode.SPLIT_SPREAD)
    val (width, height) = if (needsBounds) bounds(file) else 0 to 0
    val rotate = shouldRotateWidePage(width, height, wideMode)
    val split = shouldSplitWideReaderPage(width, height, wideMode)
    return ReaderPageInspection(
        file = file,
        wideMode = wideMode,
        revision = revision,
        usable = usable,
        rotateWide = rotate,
        canRedownload = isReaderCachePage(file, cacheRoot),
        splitWide = split,
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
    revision: ReaderImageFileRevision? = readerImageFileRevision(file),
): ReaderPageInspection? =
    inspection?.takeIf {
        file != null && it.file == file && it.wideMode == wideMode && it.revision == revision
    }
