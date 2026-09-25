package com.mangareader.app

import android.content.Context

/**
 * Applies a parsed Tachiyomi backup to MangaReader stores.
 */
/**
 * Merges [backup] into this app's stores.
 *
 * Additive, with one exception: a series the backup marks as not in the library
 * is removed from ours if it's there. That's what makes a re-import able to
 * correct an earlier one that added everything indiscriminately.
 *
 * Bulk writes throughout, and not as an optimisation. `Library.add` rewrites the
 * whole library JSON per call, so 4645 of them is quadratic and would take
 * minutes; the same goes for a `SharedPreferences.apply()` per read chapter.
 */
internal fun applyTachiyomiBackup(context: Context, backup: TachiyomiBackup): String {
    // Source names, before anything that files entries under a source id.
    //
    // Field 101 was already parsed and used only to count sources in the preview
    // dialog. It is worth more than that: a backup is the **only** place some of
    // these names survive. `SourceManager` can name what is installed and the
    // repo index can name what is installable, and neither covers a source that
    // is neither — a fork's built-in source, or an extension delisted since the
    // backup was taken. Those entries sit in the library forever with nothing
    // able to say what they are.
    //
    // The key format is the same one built below for every entry, so these land
    // on exactly the ids the library grid groups by.
    runCatching {
        SourceNames.record(
            context,
            backup.sourceNames.entries.associate { (id, name) -> "tachi:$id" to name }
        )
    }

    // Categories first: everything else references them by id.
    val byOrder = mutableMapOf<Int, String>()
    backup.categories.forEach { (order, name) ->
        val existing = Categories.list(context).firstOrNull { it.name == name }
        byOrder[order] = (existing ?: Categories.addAndGet(context, name)).id
    }

    val entries = mutableListOf<LibraryEntry>()
    val readKeys = mutableListOf<String>()
    val pages = mutableMapOf<String, Int>()
    val assignments = mutableListOf<Pair<String, Set<String>>>()
    val history = mutableListOf<HistoryEntry>()
    val notInLibrary = mutableSetOf<String>()

    backup.series.forEach { s ->
        val appSourceId = "tachi:${s.sourceId}"
        val seriesId = "${s.sourceId}:${s.url}"

        if (s.favourite) {
            entries.add(
                LibraryEntry(
                    seriesId = seriesId,
                    sourceId = appSourceId,
                    title = s.title,
                    cover = if (isLoopback(s.cover)) "" else s.cover,
                    addedAt = if (s.addedAt > 0) s.addedAt else System.currentTimeMillis()
                )
            )

            val catIds = s.categoryOrders.mapNotNull { byOrder[it] }.toSet()
            if (catIds.isNotEmpty()) assignments.add(seriesId to catIds)
        } else {
            notInLibrary.add(seriesId)
        }

        // Progress is kept for everything, library or not. It's keyed by
        // chapter, so it costs nothing to hold and it's waiting if the series
        // is ever added.
        s.chapters.forEach { c ->
            val chapterKey = "$appSourceId|${s.sourceId}:${c.url}"
            if (c.read) readKeys.add(chapterKey)
            if (c.lastPage > 0) pages[chapterKey] = c.lastPage
        }

        if (!s.favourite) return@forEach

        s.history.forEach { (url, at) ->
            val chapterKey = "$appSourceId|${s.sourceId}:$url"
            val page = pages[chapterKey] ?: 0
            history.add(
                HistoryEntry(
                    chapterKey = chapterKey,
                    title = s.title,
                    sourceId = appSourceId,
                    seriesId = seriesId,
                    coverPath = s.cover,
                    page = page,
                    // The backup doesn't record a page count. A total below the
                    // current page renders as nonsense, so this floors it.
                    total = page + 1,
                    updatedAt = at
                )
            )
        }
    }

    Library.removeAll(context, notInLibrary)
    Library.mergeAll(context, entries)
    ReadState.setReadBulk(context, readKeys)
    savePageBulk(context, pages)
    Categories.setCategoriesForMany(context, assignments.toMap())

    // History caps at 40, so only the newest are worth writing — and oldest
    // first, because each touch moves its entry to the front. Apply the batch
    // in memory and serialize the capped store once.
    History.touchAll(context, history.sortedBy { it.updatedAt }.takeLast(40))

    return buildString {
        appendLine("Imported ${entries.size} series.")
        if (notInLibrary.isNotEmpty()) {
            appendLine(
                "${notInLibrary.size} more were read but never added to the " +
                    "library, so they were left out."
            )
        }
        appendLine("${readKeys.size} chapters marked read, ${pages.size} with a saved page.")
        if (byOrder.isNotEmpty()) appendLine("${byOrder.size} categories.")
        appendLine()
        append(
            "Any series whose extension isn't installed here is in the library but " +
                "can't list chapters until you add that source."
        )
    }
}

/**
 * Whether a URL points at the machine that served it.
 *
 * Some extensions are front-ends for a server the user runs themselves, and the
 * backup stores whatever absolute cover URL that install produced. A
 * `http://127.0.0.1/image/...` meant one particular app on one particular phone;
 * carried anywhere else it's an address that answers nothing, and it renders as
 * a grid of connection errors rather than as a missing cover.
 *
 * Dropped to blank instead, so the grid shows a placeholder and the cover can be
 * filled in later from the source itself — see [Library.healCover].
 */
internal fun isLoopback(url: String): Boolean {
    val host = runCatching { android.net.Uri.parse(url).host }.getOrNull()?.lowercase()
        ?: return false
    return host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0" || host == "::1"
}
