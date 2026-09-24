package com.mangareader.app

/** One page of browse/search results, plus whether another page exists. */
data class SeriesPage(
    val series: List<Series>,
    val hasNext: Boolean,
)

/** Which listing the per-source browse screen is showing. */
enum class BrowseMode { POPULAR, LATEST, FILTER }

/**
 * Fields past [handle] are metadata for the series screen. They're all optional
 * because a source may not supply them, and because reopening a stored entry
 * deliberately skips the details request that would fill them in — see
 * [Source.restoreSeries]. A blank field means "not known", never "empty".
 */
data class Series(
    val id: String,
    val title: String,
    val cover: Any?,
    val handle: Any? = null,
    val author: String? = null,
    /**
     * Separate from [author] because sources report them separately and many
     * series have two different people in them. They were joined into one
     * string at the boundary, which made "who drew this" unanswerable and made
     * searching for either of them search for both at once.
     */
    val artist: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null
)

data class Chapter(
    val id: String,
    val name: String,
    val handle: Any? = null,
    /** Epoch millis from the source; 0 when it doesn't publish one. */
    val dateUploaded: Long = 0L,
    val scanlator: String? = null,
    /**
     * The source's own chapter number, from `SChapter.chapter_number`.
     *
     * **[NO_NUMBER] means "the source didn't say", and that is not zero.** Three
     * things arrive without one and all three are normal: a chapter from a
     * source that publishes no numbering, a chapter rebuilt from the download
     * queue (which stores an id and a name and nothing else), and every chapter
     * in a `ChapterCache` file written before this field existed. Defaulting
     * those to `0f` would make them a real chapter zero and pile them at the top
     * of a number sort, which reads as the sort being broken rather than as data
     * being absent — the same three-state trap `SeriesIndex` documents, in a new
     * store.
     *
     * So anything sorting on this puts [NO_NUMBER] at the end, never among the
     * real ones, and anything displaying it falls back to [name].
     */
    val number: Float = NO_NUMBER
) {
    companion object {
        /** "This source published no chapter number." Negative so it can never collide with one. */
        const val NO_NUMBER = -1f
    }
}

/**
 * The result of [Source.scanVideos] — what was found, and what was there instead.
 *
 * **[note] exists because "found nothing" is not a diagnosis.** The first
 * version of this returned a bare list, and when it came back empty on a gallery
 * advertising thirteen videos there was no way to tell which of three things had
 * happened: the videos are injected by JavaScript and are not in the served HTML
 * at all; they are in a different element than the one being selected; or the
 * page served to this client differs from the one a browser sees.
 *
 * Separating those from a phone otherwise means reading page source, which
 * Android browsers do not offer. So the scan reports what the document actually
 * contained — the same instinct as the connection probe, which exists because a
 * failure that names nothing costs more than the request that would have named
 * it.
 */
data class VideoSubtitle(
    val url: String,
    val language: String = "",
)

data class PlayableVideo(
    val url: String,
    val title: String = "",
    val headers: Map<String, String> = emptyMap(),
    /** Stable episode identity; falls back to the stream URL for non-anime scans. */
    val resumeKey: String = "",
    val subtitles: List<VideoSubtitle> = emptyList(),
)

data class VideoScan(
    /** Direct, playable urls — retained for compatibility with HTML scanners. */
    val links: List<String>,
    /**
     * Iframe urls, which are where an embedded player usually lives.
     *
     * CosplayTele is the worked example: its page has ONE embedded video and no
     * <video> element, no .mp4 and no .m3u8 anywhere in 286KB of served HTML —
     * the player is built by the iframe's own script. Nothing this app can
     * select will ever reach the media file, but the iframe url IS in the HTML,
     * and handing that to a browser gets a working player.
     *
     * Kept separate from [links] because they are not the same promise. A link
     * plays; an embed opens a page that might.
     */
    val embeds: List<String> = emptyList(),
    /** What the document held, when nothing playable was found. */
    val note: String? = null,
    /** Rich stream metadata from anime extensions (quality + request headers). */
    val videos: List<PlayableVideo> = links.map { PlayableVideo(it) },
)
