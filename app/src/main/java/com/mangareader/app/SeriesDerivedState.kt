package com.mangareader.app

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class SeriesChapterDerivedSnapshot(
    val progress: SeriesProgressSummary,
    val visible: List<Chapter>,
    val chapterDisplay: ChapterDisplay,
    val filtersActive: Boolean,
)

internal data class SeriesDerivedState(
    val resumeIndex: Int,
    val visible: List<Chapter>,
    val chapterDisplay: ChapterDisplay,
    val filtersActive: Boolean,
    val downloadedCount: Int?,
    val anyProgress: Boolean,
    val chapterStateReady: Boolean,
    val listState: LazyListState,
    val barAlpha: Float,
)

@Composable
internal fun rememberSeriesDerivedState(
    context: Context,
    series: Series,
    chapters: List<Chapter>,
    effectiveReadTick: Int,
    sourceId: String,
    isAnimeSource: Boolean,
    downloadTick: Int,
    optionsTick: Int,
    inLibrary: Boolean,
    scroll: ScrollMemory,
): SeriesDerivedState {
    val appContext = context.applicationContext

    /**
     * Everything below used to run from `remember { ... }`, which still means
     * "run during composition" on the UI thread. On a long series that can be
     * hundreds or thousands of SharedPreferences reads plus filesystem probes
     * for the Downloaded filter before the first chapter row can be drawn.
     *
     * Keep the last completed snapshot visible while a new one is calculated,
     * and do the storage-backed scan on IO. `produceState` preserves its current
     * value when a key changes, so toggling a filter or finishing a chapter does
     * not blank the list while the replacement snapshot is being prepared.
     */
    val chapterSnapshot by produceState<SeriesChapterDerivedSnapshot?>(
        initialValue = null,
        chapters,
        effectiveReadTick,
        downloadTick,
        optionsTick,
        sourceId,
        isAnimeSource,
    ) {
        value = withContext(Dispatchers.IO) {
            SeriesChapterDerivedSnapshot(
                progress = seriesProgressSummary(
                    context = appContext,
                    chapters = chapters,
                    sourceId = sourceId,
                    isAnimeSource = isAnimeSource,
                ),
                visible = visibleChapters(appContext, chapters, sourceId),
                chapterDisplay = ChapterPrefs.display(appContext),
                filtersActive = ChapterPrefs.anyFilterActive(appContext),
            )
        }
    }

    val chapterStateReady = chapterSnapshot != null
    val progress = chapterSnapshot?.progress
    val resumeIndex = progress?.resumeIndex ?: -1
    val visible = chapterSnapshot?.visible.orEmpty()
    val chapterDisplay = chapterSnapshot?.chapterDisplay ?: ChapterDisplay.NAME
    val filtersActive = chapterSnapshot?.filtersActive ?: false

    val downloadedCount by produceState<Int?>(null, chapters, downloadTick) {
        value = withContext(Dispatchers.IO) {
            chapters.count { Downloads.isComplete(appContext, it.id) }
        }
    }

    // The one place in the app that has a chapter list, its source and the
    // series id in hand at the same time, which is exactly what the index needs
    // and the reason it's written from here rather than from the fetch in
    // YomuApp. Keyed on readTick as well as the list, so marking chapters read
    // — here or by finishing one in the reader, which bumps the same tick on the
    // way out — corrects the stored count rather than leaving it to drift until
    // the next fetch.
    //
    // Gated on library membership: this store only feeds the library screen, and
    // recording every series merely *browsed* would grow a JSON that gets
    // rewritten in full, for entries nothing will ever read.
    LaunchedEffect(chapters, effectiveReadTick, inLibrary, sourceId) {
        if (!inLibrary || chapters.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            SeriesIndex.record(appContext, sourceId, series.id, chapters)
        }
    }

    // Seeded before the list below composes, not in an effect: the LazyColumn
    // builds its state as it composes, so a position belonging to a different
    // series has to be gone by then rather than one frame later.
    scroll.sync(series.id)

    val anyProgress = progress?.anyProgress ?: false

    // Hoisted above the Box because the top bar and the list both read it. Built
    // inline at the LazyColumn until 0.109, which was fine while nothing else
    // needed it — construct it twice and the bar gets a state that never
    // scrolls, and the symptom is a bar that simply never fades in, which reads
    // as the alpha arithmetic being wrong rather than as two objects.
    val listState = rememberRestoredListState(scroll, "series", series.id)

    // dp converted once, out here: `firstVisibleItemScrollOffset` is in pixels,
    // so a raw pixel constant would fade over a third of the distance on a
    // high-density phone that it does on a low-density one.
    val fadeOverPx = with(LocalDensity.current) { TOP_BAR_FADE_OVER.toPx() }

    /**
     * How opaque the top bar is, from how far the header has scrolled.
     *
     * The bar sits *over* the cover backdrop rather than above it, so at rest it
     * is invisible and only the back arrow shows against the art — which is what
     * the screen looked like before it had a bar at all. It fades in as the
     * cover leaves, so the title arrives exactly when the thing it names is
     * gone. Modelled on SY's `MangaToolbar`, which takes the same two alphas
     * from its own scroll state.
     *
     * `derivedStateOf`, not a plain read: `firstVisibleItemScrollOffset` changes
     * every frame of a drag, and reading it directly would recompose the whole
     * screen — a 171-row chapter list included — on every pixel.
     *
     * [TOP_BAR_FADE_OVER] is deliberately shorter than the header: the bar wants
     * to be solid before the chapter rows reach it, not when the header ends.
     */
    val barAlpha by remember(listState, fadeOverPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / fadeOverPx).coerceIn(0f, 1f)
        }
    }


    return SeriesDerivedState(
        resumeIndex = resumeIndex,
        visible = visible,
        chapterDisplay = chapterDisplay,
        filtersActive = filtersActive,
        downloadedCount = downloadedCount,
        anyProgress = anyProgress,
        chapterStateReady = chapterStateReady,
        listState = listState,
        barAlpha = barAlpha,
    )
}
