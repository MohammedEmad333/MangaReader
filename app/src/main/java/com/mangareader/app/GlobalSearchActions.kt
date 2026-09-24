package com.mangareader.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun globalSearchMediaIsAnime(filter: String): Boolean? = when (filter) {
    "Anime" -> true
    "Manga" -> false
    else -> null
}

internal suspend fun globalSearchTargets(
    context: Context,
    configs: List<SourceConfig>,
    extensions: List<Source>,
    pinnedOnly: Boolean,
    mediaIsAnime: Boolean? = null,
): List<Source> = withContext(Dispatchers.IO) {
    val locals = configs.mapNotNull {
        runCatching { SourceManager.build(context, it) }.getOrNull()
    }
    val hidden = SourcePrefs.hiddenSources(context)
    val languages = SourcePrefs.enabledLangs(context)
    val showNsfw = SourcePrefs.showNsfw(context)

    val searchable = (locals + extensions)
        .filter { it.supportsSearch }
        .filter { mediaIsAnime == null || it.isAnime == mediaIsAnime }
        .filter {
            SourcePrefs.isVisible(
                it.id,
                it.lang.ifBlank { "Other" },
                it.isNsfw,
                hidden,
                languages,
                showNsfw
            )
        }

    if (!pinnedOnly) {
        searchable
    } else {
        val pinned = SourcePrefs.pinned(context)
        searchable.filter { it.id in pinned }
            .ifEmpty { searchable }
    }
}

internal suspend fun searchGlobalBatch(
    query: String,
    sources: List<Source>
): List<GlobalResult> = withContext(Dispatchers.IO) {
    val results = sources.map { source ->
        async {
            runCatching {
                source.searchSeries(query, 1).series
                    .take(GLOBAL_SEARCH_PER_SOURCE)
            }.getOrDefault(emptyList())
        }
    }.awaitAll()

    sources.mapIndexed { index, source ->
        GlobalResult(source, results[index])
    }
}


internal class GlobalSearchState(
    context: Context
) {
    var open by androidx.compose.runtime.mutableStateOf(false)
    var query by androidx.compose.runtime.mutableStateOf("")
    var results by androidx.compose.runtime.mutableStateOf<List<GlobalResult>>(emptyList())
    var running by androidx.compose.runtime.mutableStateOf(false)
    var done by androidx.compose.runtime.mutableIntStateOf(0)
    var total by androidx.compose.runtime.mutableIntStateOf(0)
    var pinnedOnly by androidx.compose.runtime.mutableStateOf(
        SourcePrefs.pinnedOnlySearch(context)
    )
    var hasResultsOnly by androidx.compose.runtime.mutableStateOf(true)
    var mediaFilter by androidx.compose.runtime.mutableStateOf("All")
    var recents by androidx.compose.runtime.mutableStateOf(
        SourcePrefs.recentSearches(context)
    )
    // Null = all media. During migration this is pinned to the source media type
    // so a manga cannot be migrated into an anime source (or vice versa).
    var mediaIsAnime by androidx.compose.runtime.mutableStateOf<Boolean?>(null)

    private var job: kotlinx.coroutines.Job? = null

    fun cancel() {
        job?.cancel()
        job = null
        running = false
    }

    fun setMediaFilter(
        context: Context,
        scope: kotlinx.coroutines.CoroutineScope,
        configs: List<SourceConfig>,
        extensions: List<Source>,
        value: String
    ) {
        mediaFilter = value
        if (query.isNotBlank()) {
            search(context, scope, configs, extensions, query)
        }
    }

    fun setPinnedOnly(
        context: Context,
        scope: kotlinx.coroutines.CoroutineScope,
        configs: List<SourceConfig>,
        extensions: List<Source>,
        value: Boolean
    ) {
        pinnedOnly = value
        SourcePrefs.setPinnedOnlySearch(context, value)
        if (query.isNotBlank()) {
            search(context, scope, configs, extensions, query)
        }
    }

    fun search(
        context: Context,
        scope: kotlinx.coroutines.CoroutineScope,
        configs: List<SourceConfig>,
        extensions: List<Source>,
        newQuery: String
    ) {
        job?.cancel()
        query = newQuery
        results = emptyList()
        done = 0
        total = 0

        if (newQuery.isBlank()) {
            running = false
            job = null
            return
        }

        running = true
        recents = SourcePrefs.addRecentSearch(context, newQuery)
        job = scope.launch {
            try {
                val effectiveMediaIsAnime =
                    mediaIsAnime ?: globalSearchMediaIsAnime(mediaFilter)
                val targets = globalSearchTargets(
                    context = context,
                    configs = configs,
                    extensions = extensions,
                    pinnedOnly = pinnedOnly,
                    mediaIsAnime = effectiveMediaIsAnime,
                )
                total = targets.size

                targets.chunked(GLOBAL_SEARCH_CONCURRENCY).forEach { chunk ->
                    results = results + searchGlobalBatch(newQuery, chunk)
                    done += chunk.size
                }
            } finally {
                running = false
            }
        }
    }
}
