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
     * Extra metadata the extension attaches to this chapter and reads back on
     * its own later calls.
     *
     * **Non-null, and that is load-bearing — it was `JsonObject?` until 0.85 and
     * that was the bug.** extensions-lib 1.6 declares `var memo: JsonObject`,
     * so an extension compiled against it emits no null check: Asura Scans'
     * `getChapterUrl` is `chapter.memo["mangaSlug"]?.string ?: throw ...`, which
     * compiles to a direct `getMemo().get(...)`. Handed a null it died with
     * `NullPointerException: ... JsonObject.get(Object) on a null object
     * reference` rather than reaching its own `?:`, and the fallback the
     * extension author wrote never ran.
     *
     * An empty object is therefore the correct absent value, not null. It lets
     * the extension's own missing-key handling do its job — a legible
     * "Refresh Chapter List" instead of a platform NPE.
     *
     * The JVM signature is the same either way (`getMemo()` returns
     * `JsonObject` in both), so this is binary compatible with every extension
     * already installed; Kotlin nullability is metadata, not shape.
     *
     * Still not persisted: a chapter this app rebuilds — `rehydrateChapter` for
     * a queued download — gets the empty default, and for Asura that is not
     * enough to build a page URL. `DownloadService.genuineChapter` re-lists the
     * series and uses the chapter the extension itself produced, which is the
     * only way to get a real one.
     */
    var memo: JsonObject
}
