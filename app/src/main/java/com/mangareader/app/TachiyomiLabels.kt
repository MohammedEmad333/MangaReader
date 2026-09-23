package com.mangareader.app

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SChapterImpl
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaImpl
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import eu.kanade.tachiyomi.source.model.Page as TachiPage

/**
 * Turns an extension's language code into the label shown in the sources list.
 *
 * Extensions use ISO codes plus Tachiyomi's own "all" for multi-language
 * sources. Anything not listed falls back to the uppercased code, so a source
 * in a language nobody mapped still reads sensibly instead of showing blank.
 */
fun langLabel(code: String): String = when (code.lowercase()) {
    "all" -> "Multi"
    "other" -> "Other"
    "en" -> "English"
    "ja" -> "Japanese"
    "ko" -> "Korean"
    "zh" -> "Chinese"
    "es" -> "Spanish"
    "es-419" -> "Spanish (LatAm)"
    "fr" -> "French"
    "de" -> "German"
    "it" -> "Italian"
    "pt" -> "Portuguese"
    "pt-br" -> "Portuguese (BR)"
    "ru" -> "Russian"
    "id" -> "Indonesian"
    "vi" -> "Vietnamese"
    "th" -> "Thai"
    "ar" -> "Arabic"
    "tr" -> "Turkish"
    "pl" -> "Polish"
    "uk" -> "Ukrainian"
    "fa" -> "Persian"
    "hi" -> "Hindi"
    "fil" -> "Filipino"
    "ms" -> "Malay"
    "nl" -> "Dutch"
    "ca" -> "Catalan"
    "he" -> "Hebrew"
    "cs" -> "Czech"
    "hu" -> "Hungarian"
    "ro" -> "Romanian"
    "bg" -> "Bulgarian"
    "el" -> "Greek"
    "sv" -> "Swedish"
    "no", "nb" -> "Norwegian"
    "da" -> "Danish"
    "fi" -> "Finnish"
    else -> code.uppercase()
}

/** SManga.status is an int enum; this is its display form. */
fun statusLabel(status: Int): String? = when (status) {
    SManga.ONGOING -> "Ongoing"
    SManga.COMPLETED -> "Completed"
    SManga.LICENSED -> "Licensed"
    SManga.PUBLISHING_FINISHED -> "Publishing finished"
    SManga.CANCELLED -> "Cancelled"
    SManga.ON_HIATUS -> "On hiatus"
    else -> null
}
