package com.mangareader.app

import android.content.Context
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import eu.kanade.tachiyomi.source.model.Page as TachiPage

/**
 * Owns page-list validation, progressive page loading, retries, connection
 * recycling and persistence for one Tachiyomi extension source.
 */
internal class TachiyomiPageLoader(
    private val delegate: CatalogueSource,
    private val context: Context,
) {
    suspend fun loadPagesProgressively(
        chapter: Chapter,
        persist: Boolean,
        startAt: Int,
        onUpdate: suspend (List<File?>) -> Unit,
    ) = coroutineScope {
        if (Downloads.isComplete(context, chapter.id)) {
            onUpdate(Downloads.pages(context, chapter.id))
            return@coroutineScope
        }

        val sChapter = chapter.handle as? SChapter
        if (sChapter == null) {
            onUpdate(emptyList())
            if (persist) {
                throw ChapterDownloadException(
                    0,
                    0,
                    IllegalStateException("No chapter handle")
                )
            }
            return@coroutineScope
        }

        val pages = cappedPageList(sChapter)
        if (persist) {
            val stored = Downloads.dirFor(context, chapter.id)
            val marker = File(stored, ".complete")
            if (marker.exists()) {
                if (!canSafelyRepairOfflineChapter(stored, pages.size)) {
                    throw ChapterDownloadException(
                        0, pages.size,
                        IllegalStateException(
                            "Stored chapter needs manual review: ambiguous files or page count changed"
                        )
                    )
                }
            }
        }
        val dir = if (persist) {
            Downloads.dirFor(context, chapter.id)
        } else {
            Downloads.cacheDirFor(context, chapter.id)
        }
        dir.mkdirs()

        val done = arrayOfNulls<File>(pages.size)
        onUpdate(done.toList())

        val firstError = AtomicReference<Throwable?>(null)
        val priorityKey = chapterKeyOf("tachi:${delegate.id}", chapter)
        val priorityTicket = if (persist) null else ReaderPagePriority.begin(priorityKey, startAt)
        val started = mutableSetOf<Int>()
        var attempted = 0
        var failedSoFar = 0
        var batch = 0
        val hostFailures = ConcurrentHashMap<String, Int>()

        try {
        while (started.size < pages.size) {
            // Re-evaluate at every batch, so a jump to page 10 does not
            // wait for pages 3-9 to finish before page 10 is requested.
            val chunk = prioritizedPageOrder(
                pages.size, priorityTicket?.get() ?: startAt, started,
                if (priorityTicket == null) emptyList() else ReaderPagePriority.visiblePages(priorityKey),
                if (priorityTicket == null) 1 else ReaderPagePriority.direction(priorityKey)
            ).take(PAGE_CONCURRENCY)
            if (chunk.isEmpty()) break
            started.addAll(chunk)
            chunk.map { index ->
                async {
                    val outcome = try {
                        Result.success(downloadPage(pages[index], dir, index, hostFailures))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Result.failure<File>(error)
                    }
                    outcome.exceptionOrNull()?.let {
                        firstError.compareAndSet(null, it)
                    }
                    Triple(index, outcome.getOrNull(), outcome.exceptionOrNull())
                }
            }.awaitAll().let { results ->
                results.forEach { (index, file, _) -> done[index] = file }
                attempted += results.size
                failedSoFar += results.count { (_, file, _) -> file == null }

                results.mapNotNull { (index, file, _) ->
                    if (file != null) hostOf(pages[index].imageUrl) else null
                }.toSet().forEach { hostFailures.remove(it) }
            }

            onUpdate(done.toList())

            val dead = hostFailures.entries
                .firstOrNull { it.value >= HOST_CONNECT_FAILURE_LIMIT }
            if (dead != null) {
                val stopped = IOException(
                    "Could not connect to ${dead.key} — gave up after " +
                        "${dead.value} consecutive failures across $attempted " +
                        "of ${pages.size} pages"
                )
                if (persist) {
                    throw ChapterDownloadException(
                        failedSoFar,
                        pages.size,
                        stopped
                    )
                }
                throw stopped
            }

            batch++
            if (started.size < pages.size) {
                if (batch % CONNECTION_RECYCLE_BATCHES == 0) {
                    recycleConnections()
                }
                delay(BATCH_GAP_MS)
            }
        }

        // Retry failed reader pages once, without re-fetching successful pages.
        // Keep user-visible pages first, and leave persistent downloads alone.
        if (!persist && done.any { it == null }) {
            val retryOrder = readerFailedPageRetryOrder(
                done.map { it != null },
                priorityTicket?.get() ?: startAt,
                ReaderPagePriority.visiblePages(priorityKey),
                ReaderPagePriority.direction(priorityKey)
            )
            for (index in retryOrder) {
                if (hostFailures.values.any { it >= HOST_CONNECT_FAILURE_LIMIT }) break
                val recovered = try {
                    downloadPage(pages[index], dir, index, hostFailures)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (recovered != null) {
                    done[index] = recovered
                    onUpdate(done.toList())
                }
            }
        }
        } finally {
            if (priorityTicket != null) ReaderPagePriority.finish(priorityKey, priorityTicket)
        }

        if (!persist) return@coroutineScope

        val failed = done.count { it == null }
        if (pages.isNotEmpty() && failed == 0) {
            Downloads.markComplete(context, chapter.id, pages.size)
        } else {
            throw ChapterDownloadException(failed, pages.size, firstError.get())
        }
    }

    suspend fun loadPages(chapter: Chapter): List<File> {
        val sChapter = chapter.handle as? SChapter ?: return emptyList()
        val pages = cappedPageList(sChapter)

        if (Downloads.isComplete(context, chapter.id)) {
            return Downloads.pages(context, chapter.id)
        }

        val dir = Downloads.cacheDirFor(context, chapter.id).apply { mkdirs() }
        val hostFailures = ConcurrentHashMap<String, Int>()
        return pages.mapIndexedNotNull { index, page ->
            try {
                downloadPage(page, dir, index, hostFailures)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun cappedPageList(sChapter: SChapter): List<TachiPage> {
        val pages = try {
            withTimeout(PAGE_LIST_TIMEOUT_MS) {
                delegate.getPageList(sChapter)
            }
        } catch (e: TimeoutCancellationException) {
            throw IOException(
                "This source took more than ${PAGE_LIST_TIMEOUT_MS / 1000}s to list " +
                    "the pages of one chapter and was given up on. The extension may be " +
                    "stuck following its own pagination."
            )
        }

        if (pages.size > PAGE_LIST_MAX) {
            throw IOException(
                "This source returned ${pages.size} pages for one chapter, past the " +
                    "$PAGE_LIST_MAX-page limit. That is almost certainly the extension " +
                    "walking the site rather than the chapter."
            )
        }
        return pages
    }

    private val httpClient: OkHttpClient?
        get() = (delegate as? HttpSource)?.client

    private fun recycleConnections() {
        runCatching { httpClient?.connectionPool?.evictAll() }
    }

    private fun hostOf(url: String?): String? =
        if (url.isNullOrBlank()) null
        else runCatching { java.net.URI(url).host }.getOrNull()

    private fun isConnectFailure(error: Throwable?): Boolean {
        var e = error
        var depth = 0
        while (e != null && depth++ < CAUSE_CHAIN_LIMIT) {
            if (e is java.net.SocketTimeoutException || e is java.net.ConnectException) {
                return true
            }
            e = e.cause
        }
        return false
    }

    private suspend fun downloadPage(
        page: TachiPage,
        dir: File,
        index: Int,
        hostFailures: ConcurrentHashMap<String, Int>,
    ): File {
        var attempt = 0
        while (true) {
            try {
                return fetchPage(page, dir, index)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++

                val deadHost = isConnectFailure(e) &&
                    hostOf(page.imageUrl)?.let { host ->
                        hostFailures.merge(host, 1, Int::plus)!! >= CONNECT_RETRY_GIVE_UP_AT
                    } == true

                if (attempt >= PAGE_ATTEMPTS || !isTransient(e) || deadHost) {
                    throw PageDownloadException(index, page.imageUrl, e)
                }

                recycleConnections()
                delay(
                    PAGE_RETRY_BASE_MS * (1L shl (attempt - 1)) +
                        Random.nextLong(PAGE_RETRY_JITTER_MS)
                )
            }
        }
    }

    private fun isTransient(e: Throwable): Boolean = when (e) {
        is HttpException -> e.code in TRANSIENT_HTTP_CODES
        is IOException -> true
        else -> false
    }

    private suspend fun fetchPage(page: TachiPage, dir: File, index: Int): File {
        val http = delegate as? HttpSource

        if (page.imageUrl.isNullOrEmpty() && http != null) {
            page.imageUrl = http.getImageUrl(page)
        }
        page.imageUrl = page.imageUrl?.repointFromLoopback()

        val target = File(dir, "%04d".format(index))
        if (target.exists()) {
            // A zero-byte file is also a failed previous download, not a cache hit.
            if (target.length() > 0L &&
                target.inputStream().use { input ->
                    val prefix = input.readPagePrefix()
                    !isObviouslyInvalidPage(prefix) && !isTruncatedKnownImage(prefix, target.length())
                }
            ) return target
            if (!target.delete()) {
                throw IOException("Could not remove invalid cached chapter page")
            }
        }

        val body = if (http != null) {
            http.getImage(page).body!!
        } else {
            val url = page.imageUrl ?: error("No image url for page $index")
            fallbackClient.newCall(
                Request.Builder().url(url).build()
            ).execute().body!!
        }

        val partial = File(dir, "%04d.part".format(index))
        try {
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (partial.inputStream().use { input ->
                    val prefix = input.readPagePrefix()
                    isObviouslyInvalidPage(prefix) || isTruncatedKnownImage(prefix, partial.length())
                }) {
                throw IOException("The source returned an HTML/JSON response instead of a chapter image")
            }
            if (!partial.renameTo(target)) {
                throw IOException("Could not save the downloaded chapter page")
            }
            return target
        } finally {
            // Interrupted transfers and invalid CDN responses must not leave
            // a stale temporary image for a later retry.
            partial.delete()
        }
    }

    private fun String.repointFromLoopback(): String {
        val url = toHttpUrlOrNull() ?: return this
        if (!url.host.isLoopback()) return this
        val base = (delegate as? HttpSource)?.baseUrl?.toHttpUrlOrNull() ?: return this
        if (base.host.isLoopback()) return this
        return url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
            .toString()
    }

    private fun String.isLoopback(): Boolean =
        this == "localhost" ||
            this == "::1" ||
            this == "0.0.0.0" ||
            startsWith("127.")

    private companion object {
        const val PAGE_CONCURRENCY = 2
        const val PAGE_ATTEMPTS = 3
        const val PAGE_LIST_MAX = 2000
        const val PAGE_LIST_TIMEOUT_MS = 180_000L
        const val PAGE_RETRY_BASE_MS = 750L
        const val PAGE_RETRY_JITTER_MS = 250L
        const val BATCH_GAP_MS = 200L
        const val CONNECTION_RECYCLE_BATCHES = 4
        const val HOST_CONNECT_FAILURE_LIMIT = 4
        const val CONNECT_RETRY_GIVE_UP_AT = 2
        const val CAUSE_CHAIN_LIMIT = 6
        val TRANSIENT_HTTP_CODES = setOf(400, 408, 425, 429, 500, 502, 503, 504)
        val fallbackClient = OkHttpClient()
    }
}

/**
 * Read only the first page bytes using InputStream APIs available on Android 7+.
 * InputStream.readNBytes was introduced in Android API 33 and cannot be called
 * directly by this application's minSdk 24 users.
 */
internal fun InputStream.readPagePrefix(): ByteArray {
    val prefix = ByteArray(128)
    var total = 0
    while (total < prefix.size) {
        val read = read(prefix, total, prefix.size - total)
        if (read <= 0) break
        total += read
    }
    return prefix.copyOf(total)
}

/**
 * A 200 response can still contain a CDN block/rate-limit document instead of an image.
 * Keep only unmistakable text/error signatures: image bytes are still accepted by
 * their decoders, including extensionless AVIF/HEIF, JPEG, PNG and WebP.
 */
internal fun isObviouslyInvalidPage(prefix: ByteArray): Boolean {
    if (prefix.isEmpty()) return true
    val start = prefix.toString(Charsets.UTF_8)
        .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        .lowercase()
    if (start.isEmpty()) return true
    if (start.startsWith("{") || start.startsWith("[")) return true // JSON error payload
    if (listOf("<!doctype html", "<html", "<head", "<body", "<script", "<?xml")
            .any(start::startsWith)) return true
    return listOf(
        "access denied", "access forbidden", "forbidden", "unauthorized",
        "403 forbidden", "404 not found", "429 too many requests",
        "502 bad gateway", "503 service unavailable", "504 gateway timeout",
        "too many requests", "rate limit exceeded", "error 403",
        "error 404", "upstream connect error", "cloudflare",
    ).any(start::startsWith)
}


/**
 * Detect unmistakably incomplete image headers without rejecting unfamiliar
 * formats (including extensionless AVIF/HEIF and newer codec variants).
 */
internal fun isTruncatedKnownImage(prefix: ByteArray, fileSize: Long): Boolean {
    fun begins(vararg bytes: Int): Boolean =
        prefix.size >= bytes.size &&
            bytes.indices.all { prefix[it].toInt() and 0xff == bytes[it] }
    return when {
        begins(0x89, 0x50, 0x4e, 0x47) -> fileSize < 24L // PNG IHDR
        begins(0xff, 0xd8, 0xff) -> fileSize < 4L // JPEG marker
        begins(0x47, 0x49, 0x46, 0x38) -> fileSize < 10L // GIF header and dimensions
        begins(0x52, 0x49, 0x46, 0x46) &&
            prefix.size >= 12 &&
            prefix.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) -> fileSize < 16L
        prefix.size >= 8 &&
            prefix.copyOfRange(4, 8).contentEquals("ftyp".toByteArray()) -> fileSize < 12L
        else -> false
    }
}
