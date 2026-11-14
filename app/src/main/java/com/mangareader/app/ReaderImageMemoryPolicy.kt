package com.mangareader.app

import coil.request.CachePolicy

/** Keep small page-picker previews cached while limiting full-resolution reader images. */
internal enum class ReaderImageUsage {
    FULL_PAGE,
    PAGE_PREVIEW,
}

/**
 * Coil's memory cache stores decoded bitmaps, not just the compressed page file.
 * A handful of full-resolution manga pages can crowd out thumbnails and covers,
 * especially when a reader rapidly flips or jumps across many pages.
 *
 * The active image composable still owns its displayed bitmap. Full-size page
 * requests may read a matching cached bitmap but must not insert another into
 * the process-wide LRU cache. Previews remain cached at their bounded decode
 * size. The file on disk is untouched, and zoom resolution is unchanged.
 */
internal fun readerImageMemoryCachePolicy(usage: ReaderImageUsage): CachePolicy =
    when (usage) {
        ReaderImageUsage.FULL_PAGE -> CachePolicy.READ_ONLY
        ReaderImageUsage.PAGE_PREVIEW -> CachePolicy.ENABLED
    }
