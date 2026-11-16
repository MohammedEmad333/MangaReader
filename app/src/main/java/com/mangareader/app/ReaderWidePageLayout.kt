package com.mangareader.app

/**
 * Resolve automatic wide-page rotation from the current viewport, not from a
 * persisted device orientation. In landscape, preserve original image orientation.
 * Explicit Fit / Rotate 90° choices remain unchanged.
 *
 * The file inspection still verifies image dimensions before rotating an image.
 */
internal fun effectiveReaderWidePageMode(
    requested: ReaderWidePageMode,
    viewportWidthDp: Int,
    viewportHeightDp: Int,
): ReaderWidePageMode = when (requested) {
    ReaderWidePageMode.AUTO_PORTRAIT ->
        if (viewportWidthDp > 0 && viewportHeightDp > viewportWidthDp)
            ReaderWidePageMode.ROTATE_RIGHT
        else ReaderWidePageMode.FIT
    else -> requested
}
