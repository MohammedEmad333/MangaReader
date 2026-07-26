package com.mangareader.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Source backed by a local folder tree, with nested-folder scanning:
 * - any folder that DIRECTLY contains archives is a series
 *   (its chapters = the archives directly inside it), at any depth
 * - archives sitting loose in the chosen root = single-chapter series
 *
 * So Library/Shonen/One Piece/ch01.cbz makes "One Piece" a series,
 * not "Shonen".
 */
class LocalSource(
    override val id: String,
    private val context: Context,
    private val treeUri: Uri
) : Source {

    override val name = "Local"

    override suspend fun listSeries(): List<Series> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val out = mutableListOf<Series>()

        // loose archives directly in root -> single-chapter series
        val rootChildren = root.listFiles()
        for (f in rootChildren) {
            if (!f.isDirectory && isArchive(f.name)) {
                out.add(
                    Series(
                        id = f.uri.toString(),
                        title = (f.name ?: "book").substringBeforeLast('.'),
                        cover = ensureCover(context, f.uri),
                        handle = f
                    )
                )
            }
        }

        // any nested folder that directly holds archives = a series
        fun walk(dir: DocumentFile) {
            val children = dir.listFiles()
            val direct = children
                .filter { !it.isDirectory && isArchive(it.name) }
                .sortedBy { naturalSortKey(it.name ?: "") }
            if (direct.isNotEmpty()) {
                out.add(
                    Series(
                        id = dir.uri.toString(),
                        title = dir.name ?: "series",
                        cover = ensureCover(context, direct.first().uri),
                        handle = dir
                    )
                )
            }
            for (c in children) if (c.isDirectory) walk(c)
        }
        for (c in rootChildren) if (c.isDirectory) walk(c)

        return out.sortedBy { naturalSortKey(it.title) }
    }

    override suspend fun listChapters(series: Series): List<Chapter> {
        val doc = series.handle as? DocumentFile ?: return emptyList()
        return if (doc.isDirectory) {
            doc.listFiles()
                .filter { !it.isDirectory && isArchive(it.name) }
                .sortedBy { naturalSortKey(it.name ?: "") }
                .map { f ->
                    Chapter(
                        id = f.uri.toString(),
                        name = (f.name ?: "chapter").substringBeforeLast('.'),
                        handle = f
                    )
                }
        } else {
            listOf(Chapter(id = doc.uri.toString(), name = series.title, handle = doc))
        }
    }

    override suspend fun loadPages(chapter: Chapter): List<File> {
        val uri = (chapter.handle as? DocumentFile)?.uri
            ?: (chapter.handle as? Uri)
            ?: Uri.parse(chapter.id)
        return extractPages(context, uri)
    }

    private fun isArchive(name: String?): Boolean {
        val n = (name ?: "").lowercase()
        return n.endsWith(".cbz") || n.endsWith(".zip")
    }
}

// ---------- shared archive helpers ----------

private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")

private fun isImageEntry(name: String): Boolean {
    val lower = name.lowercase()
    val base = lower.substringAfterLast('/')
    if (base.startsWith(".") || lower.startsWith("__macosx")) return false
    return IMAGE_EXTENSIONS.any { lower.endsWith(it) }
}

private val digitRegex = Regex("\\d+")

fun naturalSortKey(name: String): String =
    digitRegex.replace(name.lowercase()) { it.value.padStart(8, '0') }

fun ensureCover(context: Context, bookUri: Uri): File? {
    val coversDir = File(context.cacheDir, "covers")
    coversDir.mkdirs()
    val coverFile = File(coversDir, bookUri.toString().hashCode().toString() + ".jpg")
    if (coverFile.exists() && coverFile.length() > 0) return coverFile
    try {
        // find the first image entry by natural order
        val names = mutableListOf<String>()
        context.contentResolver.openInputStream(bookUri)?.use { ins ->
            ZipInputStream(ins).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isImageEntry(entry.name)) names.add(entry.name)
                    entry = zip.nextEntry
                }
            }
        }
        val target = names.minByOrNull { naturalSortKey(it) } ?: return null

        // read that entry fully into memory (more reliable than decodeStream)
        var bytes: ByteArray? = null
        context.contentResolver.openInputStream(bookUri)?.use { ins ->
            ZipInputStream(ins).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == target) {
                        bytes = zip.readBytes()
                        break
                    }
                    entry = zip.nextEntry
                }
            }
        }
        val data = bytes ?: return null

        // decode bounds first, then downsample large pages
        val targetW = 400
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        var sample = 1
        if (bounds.outWidth > targetW * 2) {
            var w = bounds.outWidth
            while (w / 2 >= targetW) { sample *= 2; w /= 2 }
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val full = BitmapFactory.decodeByteArray(data, 0, data.size, opts) ?: return null

        val small = if (full.width > targetW) {
            val h = (full.height.toFloat() * targetW / full.width).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(full, targetW, h, true)
        } else full
        coverFile.outputStream().use { out ->
            small.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        if (small !== full) full.recycle()
        return if (coverFile.length() > 0) coverFile else null
    } catch (e: Exception) {
        return null
    }
}

fun extractPages(context: Context, uri: Uri): List<File> {
    val pagesDir = File(context.cacheDir, "current_book")
    pagesDir.deleteRecursively()
    pagesDir.mkdirs()

    val tmp = File(context.cacheDir, "current.cbz")
    context.contentResolver.openInputStream(uri)?.use { input ->
        tmp.outputStream().use { out -> input.copyTo(out) }
    } ?: throw IllegalStateException("Cannot open the selected file")

    val pages = mutableListOf<File>()
    ZipFile(tmp).use { zip ->
        val entries = zip.entries().toList()
            .filter { !it.isDirectory && isImageEntry(it.name) }
            .sortedBy { naturalSortKey(it.name) }
        if (entries.isEmpty()) {
            throw IllegalStateException("No images found — is this a CBZ/ZIP of pages?")
        }
        entries.forEachIndexed { index, entry ->
            val ext = entry.name.substringAfterLast('.', "jpg")
            val outFile = File(pagesDir, "page_" + index.toString().padStart(4, '0') + "." + ext)
            zip.getInputStream(entry).use { ins ->
                outFile.outputStream().use { outs -> ins.copyTo(outs) }
            }
            pages.add(outFile)
        }
    }
    tmp.delete()
    return pages
}
