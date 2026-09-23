package com.mangareader.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

internal suspend fun globalSearchTargets(
    context: Context,
    configs: List<SourceConfig>,
    extensions: List<Source>,
    pinnedOnly: Boolean
): List<Source> = withContext(Dispatchers.IO) {
    val locals = configs.mapNotNull {
        runCatching { SourceManager.build(context, it) }.getOrNull()
    }
    val hidden = SourcePrefs.hiddenSources(context)
    val languages = SourcePrefs.enabledLangs(context)
    val showNsfw = SourcePrefs.showNsfw(context)

    val searchable = (locals + extensions)
        .filter { it.supportsSearch }
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
