package com.mangareader.app

import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Minimal protobuf reader for Tachiyomi/Mihon/SY backup files.
 *
 * Unknown fields are skipped by wire type so fork-specific additions remain
 * forward-compatible without mirroring the whole upstream schema.
 */
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
