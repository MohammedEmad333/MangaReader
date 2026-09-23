package com.mangareader.app

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Reads a Tachiyomi / Mihon / TachiyomiSY backup and merges it into this app.
 *
 * **Why there's a protobuf parser in here.** A `.tachibk` is a gzipped protobuf
 * message. The usual way to read one is `kotlinx-serialization-protobuf` plus a
 * set of `@ProtoNumber`-annotated classes mirroring Tachiyomi's `BackupManga`,
 * `BackupChapter` and friends. That needs the serialization plugin on `:app`
 * (it's currently only on `:source-api`), a new dependency, and — the real cost
 * — schema classes whose field numbers have to be *exactly* right. A wrong
 * `@ProtoNumber` doesn't throw. It decodes the scanlator into the title and
 * looks like your library, only wrong.
 *
 * Protobuf's wire format is self-describing enough to skip all of that: every
 * field carries its number and type, so ~40 lines of reader can walk a message
 * and pick out the handful of fields that matter, ignoring everything else by
 * construction. That last part matters here — SY adds fields the base format
 * doesn't have, and this never has to know they exist.
 *
 * The field numbers below were **read off a real 4645-series SY backup**, not
 * recalled. Anything not listed is skipped.
 *
 * ```
 * Backup          1 manga          2 category      101 source
 * BackupManga     1 source(varint) 2 url           3 title
 *                 9 thumbnailUrl  13 dateAdded    16 chapter
 *                17 categories(varint, repeated)  100 favorite  104 history
 * BackupChapter   1 url            2 name          4 read(bool)
 *                 6 lastPageRead
 * BackupHistory   1 url            2 lastRead
 * BackupCategory  1 name           2 order
 * BackupSource    1 name           2 sourceId
 * ```
 *
 * **What can't come across.** Downloads — Tachiyomi backups have never carried
 * them. And the extensions themselves: a backup names its sources but doesn't
 * contain their APKs, so anything whose extension isn't installed here imports
 * as a library entry that can list nothing until you add the source.
 */

// ---------------------------------------------------------------- wire format

private class PbReader(private val b: ByteArray) {
    var i = 0

    fun hasMore() = i < b.size

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (i < b.size) {
            val c = b[i++].toInt() and 0xff
            result = result or ((c and 0x7f).toLong() shl shift)
            if (c and 0x80 == 0) break
            shift += 7
        }
        return result
    }

    fun bytes(): ByteArray {
        val n = varint().toInt()
        if (n < 0 || i + n > b.size) {
            i = b.size
            return ByteArray(0)
        }
        val out = b.copyOfRange(i, i + n)
        i += n
        return out
    }

    fun str(): String = String(bytes(), Charsets.UTF_8)

    /** Consumes a value of [wire] without interpreting it. */
    fun skip(wire: Int) {
        when (wire) {
            0 -> varint()
            1 -> i += 8
            2 -> bytes()
            5 -> i += 4
            // An unknown wire type means the stream is no longer aligned, and
            // guessing past it produces convincing rubbish. Stop instead.
            else -> i = b.size
        }
    }
}

// ------------------------------------------------------------------ the model

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

// ----------------------------------------------------------------- the parser

private fun parseChapter(data: ByteArray): ImportedChapter {
    var url = ""
    var name = ""
    var read = false
    var page = 0
    val r = PbReader(data)
    while (r.hasMore()) {
        val key = r.varint().toInt()
        val field = key ushr 3
        val wire = key and 7
        when {
            field == 1 && wire == 2 -> url = r.str()
            field == 2 && wire == 2 -> name = r.str()
            field == 4 && wire == 0 -> read = r.varint() != 0L
            field == 6 && wire == 0 -> page = r.varint().toInt()
            else -> r.skip(wire)
        }
    }
    return ImportedChapter(url, name, read, page)
}

private fun parseHistory(data: ByteArray): Pair<String, Long> {
    var url = ""
    var last = 0L
    val r = PbReader(data)
    while (r.hasMore()) {
        val key = r.varint().toInt()
        val field = key ushr 3
        val wire = key and 7
        when {
            field == 1 && wire == 2 -> url = r.str()
            field == 2 && wire == 0 -> last = r.varint()
            else -> r.skip(wire)
        }
    }
    return url to last
}

private fun parseSeries(data: ByteArray): ImportedSeries {
    var sourceId = 0L
    var url = ""
    var title = ""
    var cover = ""
    var added = 0L
    var favourite = true
    val cats = mutableListOf<Int>()
    val chapters = mutableListOf<ImportedChapter>()
    val history = mutableListOf<Pair<String, Long>>()
    val r = PbReader(data)
    while (r.hasMore()) {
        val key = r.varint().toInt()
        val field = key ushr 3
        val wire = key and 7
        when {
            field == 1 && wire == 0 -> sourceId = r.varint()
            field == 2 && wire == 2 -> url = r.str()
            field == 3 && wire == 2 -> title = r.str()
            field == 9 && wire == 2 -> cover = r.str()
            field == 13 && wire == 0 -> added = r.varint()
            field == 16 && wire == 2 -> chapters.add(parseChapter(r.bytes()))
            field == 17 && wire == 0 -> cats.add(r.varint().toInt())
            field == 100 && wire == 0 -> favourite = r.varint() != 0L
            field == 104 && wire == 2 -> history.add(parseHistory(r.bytes()))
            else -> r.skip(wire)
        }
    }
    return ImportedSeries(
        sourceId, url, title, cover, added, cats, favourite, chapters, history
    )
}

private fun parseNamed(data: ByteArray): Pair<String, Long> {
    var name = ""
    var num = 0L
    val r = PbReader(data)
    while (r.hasMore()) {
        val key = r.varint().toInt()
        val field = key ushr 3
        val wire = key and 7
        when {
            field == 1 && wire == 2 -> name = r.str()
            field == 2 && wire == 0 -> num = r.varint()
            else -> r.skip(wire)
        }
    }
    return name to num
}

/** Reads and decompresses [file]. Throws if it isn't a backup this understands. */
internal fun readTachiyomiBackup(file: File): TachiyomiBackup {
    val raw = file.readBytes()
    if (raw.size < 2) throw IllegalArgumentException("File is empty.")
    // Gzip magic. Some exports are plain protobuf, so this is a check rather
    // than an assumption.
    val data = if (raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()) {
        GZIPInputStream(raw.inputStream()).use { it.readBytes() }
    } else {
        raw
    }

    val series = mutableListOf<ImportedSeries>()
    val categories = mutableListOf<Pair<Int, String>>()
    val sources = mutableMapOf<Long, String>()

    val r = PbReader(data)
    while (r.hasMore()) {
        val key = r.varint().toInt()
        val field = key ushr 3
        val wire = key and 7
        when {
            field == 1 && wire == 2 -> series.add(parseSeries(r.bytes()))
            field == 2 && wire == 2 -> {
                val (name, order) = parseNamed(r.bytes())
                categories.add(order.toInt() to name)
            }
            field == 101 && wire == 2 -> {
                val (name, id) = parseNamed(r.bytes())
                if (id != 0L) sources[id] = name
            }
            else -> r.skip(wire)
        }
    }

    if (series.isEmpty()) {
        throw IllegalArgumentException(
            "No series found. This may not be a Tachiyomi backup, or it may be a " +
                "format this doesn't read."
        )
    }
    return TachiyomiBackup(series, categories, sources)
}

// ------------------------------------------------------------------ the merge

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
    assignments.forEach { (id, cats) -> Categories.setCategoriesFor(context, id, cats) }

    // History caps at 40, so only the newest are worth writing — and oldest
    // first, because each touch() moves its entry to the front.
    history.sortedBy { it.updatedAt }.takeLast(40).forEach { History.touch(context, it) }

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

// ------------------------------------------------------------- finding a file

/**
 * Looks for `.tachibk` files on external storage.
 *
 * A directory walk rather than a document picker because this app already holds
 * MANAGE_EXTERNAL_STORAGE (see the note on `StorageLocation`), so it can simply
 * read the file. Depth-capped and `Android/` is skipped, or this walks the whole
 * card looking at every app's private data.
 */
internal fun findBackupFiles(): List<File> {
    val root = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        ?: return emptyList()
    val found = mutableListOf<File>()

    fun walk(dir: File, depth: Int) {
        if (depth > 5 || found.size >= 40) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        children.forEach { f ->
            when {
                f.isDirectory && f.name != "Android" && !f.name.startsWith(".") ->
                    walk(f, depth + 1)
                f.isFile && (f.name.endsWith(".tachibk") || f.name.endsWith(".proto.gz")) ->
                    found.add(f)
                else -> Unit
            }
        }
    }
    walk(root, 0)
    return found.sortedByDescending { it.lastModified() }
}

// ----------------------------------------------------------------- the dialog
