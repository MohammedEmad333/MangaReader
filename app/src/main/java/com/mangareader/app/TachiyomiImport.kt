package com.mangareader.app

/**
 * Models shared by Tachiyomi/Mihon backup parsing and import.
 *
 * A .tachibk contains library state, chapter progress, history, categories and
 * source names. Downloads and extension APKs are not part of the backup.
 */

internal data class ImportedChapter(
    val url: String,
    val name: String,
    val read: Boolean,
    val lastPage: Int
)

internal data class ImportedSeries(
    val sourceId: Long,
    val url: String,
    val title: String,
    val cover: String,
    val addedAt: Long,
    val categoryOrders: List<Int>,
    /**
     * Whether the series is actually in the library.
     *
     * A backup also carries series you've only read from — opened once, never
     * added — so that their progress survives. Field 100 defaults to true and
     * the encoder omits defaults, so its absence means favourite and only a
     * false is ever written.
     */
    val favourite: Boolean,
    val chapters: List<ImportedChapter>,
    /** chapter url to last-read timestamp. */
    val history: List<Pair<String, Long>>
)

internal data class TachiyomiBackup(
    val series: List<ImportedSeries>,
    /** category order to name, in backup order. */
    val categories: List<Pair<Int, String>>,
    val sourceNames: Map<Long, String>
) {
    val inLibrary: Int get() = series.count { it.favourite }
    val historyOnly: Int get() = series.count { !it.favourite }
    val chapters: Int get() = series.sumOf { it.chapters.size }
    val readChapters: Int get() = series.sumOf { s -> s.chapters.count { it.read } }
    val savedPages: Int get() = series.sumOf { s -> s.chapters.count { it.lastPage > 0 } }
    val historyEntries: Int get() = series.sumOf { it.history.size }
}
