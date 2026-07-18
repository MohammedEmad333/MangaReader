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
 * Source backed by a local folder tree:
 * - each subfolder containing archives = one series (chapters = its .cbz/.zip files)
 * - each loose .cbz/.zip in the root = a single-chapter series
 */
class LocalSource(
    private val context: Context,
    private val treeUri: Uri
) : Source {

    override val name = "Local"

    override suspend fun listSeries(): List<Series> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val result = mutableListOf<Series>()
        for (child in root.listFiles()) {
            if (child.isDirectory) {
                val archives = findArchives(child)
                if (archives.isNotEmpty()) {
                    result.add(
                        Series(
                            id = child.uri.toString(),
                            title = child.name ?: "series",
                            cover = ensureCover(context, archives.first().uri),
                            handle = child
                        )
                    )
                }
            } else if (isArchive(child.name)) {
                result.add(
                    Series(
                        id = child.uri.toString(),
                        title = (child.name ?: "book").substringBeforeLast('.'),
                        cover = ensureCover(context, child.uri),
                        handle = child
                    )
                )
            }
        }
        return result.sortedBy { naturalSortKey(it.title) }
    }

    override suspend fun listChapters(series: Series): List<Chapter> {
        val doc = series.handle as? DocumentFile ?: return emptyList()
        return if (doc.isDirectory) {
            findArchives(doc).map { f ->
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

    private fun findArchives(dir: DocumentFile): List<DocumentFile> {
        val found = mutableListOf<DocumentFile>()
        fun walk(d: DocumentFile) {
            for (f in d.listFiles()) {
                if (f.isDirectory) walk(f)
                else if (isArchive(f.name)) found.add(f)
            }
        }
        walk(dir)
        return found.sortedBy { naturalSortKey(it.name ?: "") }
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
    if (coverFile.exists()) return coverFile
    try {
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
        var bmp: Bitmap? = null
        context.contentResolver.openInputStream(bookUri)?.use { ins ->
            ZipInputStream(ins).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == target) {
                        bmp = BitmapFactory.decodeStream(zip)
                        break
                    }
                    entry = zip.nextEntry
                }
            }
        }
        val full = bmp ?: return null
        val targetW = 300
        val small = if (full.width > targetW) {
            val h = (full.height.toFloat() * targetW / full.width).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(full, targetW, h, true)
        } else full
        coverFile.outputStream().use { out ->
            small.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        if (small !== full) full.recycle()
        return coverFile
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
