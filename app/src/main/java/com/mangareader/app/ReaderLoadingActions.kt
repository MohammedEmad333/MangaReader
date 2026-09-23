package com.mangareader.app

internal suspend fun streamChapterPages(
    source: Source,
    chapter: Chapter,
    resumeAt: Int,
    onPartial: suspend (List<java.io.File?>) -> Unit
) {
    source.loadPagesProgressively(
        chapter,
        persist = false,
        startAt = resumeAt,
        onUpdate = onPartial
    )
}
