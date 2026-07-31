package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonObject
import java.io.Serializable

interface SManga : Serializable {

    var url: String

    var title: String

    var artist: String?

    var author: String?

    var description: String?

    var genre: String?

    var status: Int

    var thumbnail_url: String?

    var update_strategy: UpdateStrategy

    var initialized: Boolean

    fun getGenres(): List<String>? {
        if (genre.isNullOrBlank()) return null
        return genre?.split(", ")?.map { it.trim() }?.filterNot { it.isBlank() }?.distinct()
    }

    fun copy() = create().also {
        it.url = url
        it.title = title
        it.artist = artist
        it.author = author
        it.description = description
        it.genre = genre
        it.status = status
        it.thumbnail_url = thumbnail_url
        it.update_strategy = update_strategy
        it.initialized = initialized
    }

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6

        fun create(): SManga {
            return SMangaImpl()
        }
    }

    /**
     * Extra metadata the extension attaches to this series and reads back on
     * its own later calls.
     *
     * **Non-null. See the twin note on [SChapter.memo] for why that is the whole
     * fix** — extensions-lib 1.6 declares it non-null, so extension code carries
     * no null check and a null here is an immediate `NullPointerException` on
     * `JsonObject.get`.
     *
     * This side is what made a library entry unopenable on Asura Scans while
     * Browse worked. `restoreSeries` builds an SManga from a stored url, so it
     * has no memo to offer; Asura's `getMangaUrl` reads
     * `manga.memo["slug"]?.string` and *does* have a fallback — it looks the
     * slug up in its own stored map, or derives it from the url — but a null
     * memo threw before that fallback could run. With an empty object it runs,
     * `getMangaUpdate` fetches, and the chapter list comes back.
     *
     * The general form, worth keeping: **when a vendored API relaxes a type the
     * real one declares strictly, every carefully written fallback on the other
     * side of the boundary is dead code.** Nullability that costs nothing to
     * widen locally is a contract change to the code compiled against it.
     */
    var memo: JsonObject
}
