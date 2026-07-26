package com.mangareader.app

import java.io.File

/**
 * A source of manga. Today: the local folder. Tomorrow: any implementation
 * of this interface (extension APKs plug in at exactly this seam).
 */
interface Source {
    val id: String,
    val name: String

    /** All series this source offers. */
    suspend fun listSeries(): List<Series>

    /** Chapters of one series, in reading order. */
    suspend fun listChapters(series: Series): List<Chapter>

    /** Extract/download the pages of a chapter, ready to display. */
    suspend fun loadPages(chapter: Chapter): List<File>
}

data class Series(
    val id: String,
    val title: String,
    val cover: File?,
    val handle: Any? = null
)

data class Chapter(
    val id: String,
    val name: String,
    val handle: Any? = null
)
