package com.mangareader.app

/**
 * Chooses a useful global-search query for "Find similar".
 *
 * Genres are preferred because the existing genre chips already use global
 * source search successfully. Very broad format/content labels are skipped when
 * a more descriptive genre exists. Author/artist are fallbacks for sources that
 * do not publish genres.
 */
internal object SeriesDiscovery {
    private val broadTags = setOf(
        "manga", "manhwa", "manhua", "webtoon", "comic", "comics",
        "adult", "mature", "smut", "hentai", "ecchi",
    )

    fun similarQuery(series: Series): String? {
        val genres = series.genres
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return genres.firstOrNull { it.lowercase() !in broadTags }
            ?: genres.firstOrNull()
            ?: series.author?.trim()?.takeIf { it.isNotEmpty() }
            ?: series.artist?.trim()?.takeIf { it.isNotEmpty() }
    }
}
