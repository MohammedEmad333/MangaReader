package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonObject
import java.io.Serializable

interface SChapter : Serializable {

    var url: String

    var name: String

    var date_upload: Long

    var chapter_number: Float

    var scanlator: String?

    fun copyFrom(other: SChapter) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        chapter_number = other.chapter_number
        scanlator = other.scanlator
    }

    companion object {
        fun create(): SChapter {
            return SChapterImpl()
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
