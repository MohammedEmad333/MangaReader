package com.mangareader.app

import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class TransientRouteResult {
    NONE,
    CONTENT,
    EMBED
}

@Composable
internal fun RootTransientRoute(
    root: RootRouteContext
): TransientRouteResult {
    val context = root.context
    val appState = root.app
    val browseState = root.browse
    val seriesState = root.series
    val readerSession = root.reader
    val mediaState = root.media
    val actions = root.actions
    val embed = mediaState.embed
    if (appState.challengeUrl == null && embed != null) {
        EmbedPlayerRoute(
            embed = embed,
            media = mediaState.media,
            onMediaFound = { mediaState.media = it },
            onDismissMedia = { mediaState.media = null },
            onBack = { mediaState.embed = null },
            onPlayerError = { appState.error = it }
        )
        return TransientRouteResult.EMBED
    }

    val challenge = appState.challengeUrl
    if (challenge != null) {
        ChallengeRoute(
            url = challenge,
            onSolved = {
                appState.challengeUrl = null
                val openSeriesAgain = seriesState.active
                if (openSeriesAgain != null) {
                    val origin = seriesState.origin
                    actions.openSeries(openSeriesAgain)
                    seriesState.origin = origin
                } else {
                    browseState.source?.let {
                        actions.openSource(
                            it,
                            browseState.query,
                            browseState.mode
                        )
                    }
                }
            },
            onBack = { appState.challengeUrl = null }
        )
        return TransientRouteResult.CONTENT
    }

    val chapterIndex = readerSession.chapterIndex
    val chapter = chapterIndex?.let {
        seriesState.chapters.getOrNull(it)
    }
    if (
        chapterIndex != null &&
        chapter != null &&
        readerSession.pages.isNotEmpty()
    ) {
        ReaderRoute(
            pages = readerSession.pages,
            stillLoading = readerSession.loading,
            sourceId = browseState.sourceId.orEmpty(),
            series = seriesState.active,
            chapter = chapter,
            chapters = seriesState.chapters,
            chapterIndex = chapterIndex,
            onOpenChapter = { actions.openChapter(it) },
            onClose = {
                readerSession.close()
                appState.readTick++
                root.scope.launch {
                    ReaderHistoryWriter.flush()
                    appState.history = withContext(Dispatchers.IO) {
                        History.forDisplay(context)
                    }
                }
            }
        )
        return TransientRouteResult.CONTENT
    }

    return TransientRouteResult.NONE
}
