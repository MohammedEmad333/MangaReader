package com.mangareader.app

/**
 * A chapter index alone is not an identity: different sources and series can
 * have a chapter at the same index, and some sources reuse chapter IDs.
 */
internal data class ReaderSessionIdentity(
    val sourceId: String,
    val seriesId: String,
    val chapterId: String,
) {
    fun isValid(): Boolean =
        sourceId.isNotBlank() && seriesId.isNotBlank() && chapterId.isNotBlank()
}

/** An explicit retry must target the exact currently displayed chapter. */
internal fun canRetryReaderSession(
    active: ReaderSessionIdentity?,
    requested: ReaderSessionIdentity,
    activeIndex: Int?,
    requestedIndex: Int,
    loading: Boolean,
): Boolean =
    !loading && requested.isValid() && active == requested && activeIndex == requestedIndex
