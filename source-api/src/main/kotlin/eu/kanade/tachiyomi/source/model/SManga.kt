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
     * Free-form scratch space for the extension, carried between its own calls.
     *
     * Not used by this app and deliberately not persisted — it is the
     * extension's, and it holds whatever that extension wants to remember about
     * this object between the call that produced it and the calls that consume
     * it. Asura Scans 1.6.66 stashes the raw JSON it parsed a series out of and
     * reads it back in `getMangaUpdate`, which saves re-fetching and re-parsing.
     *
     * **It exists here because extensions call `setMemo`/`getMemo` directly.**
     * Without it they die with
     * `NoSuchMethodError: No interface method setMemo(...)` the moment they
     * parse anything, which presents as a source that lists nothing at all.
     *
     * Nullable, and null is normal: anything this app rebuilds rather than
     * receives — `restoreSeries` for a library entry, `rehydrateChapter` for a
     * queued download — has no memo, because only the extension can set one. An
     * extension that requires its own memo to be present will not work through
     * those paths. Nothing observed needs that yet; if a source works from
     * Browse and fails from Library, this is the first thing to suspect.
     */
    var memo: JsonObject?
}
