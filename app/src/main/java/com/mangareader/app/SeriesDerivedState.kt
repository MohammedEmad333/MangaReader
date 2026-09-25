package com.mangareader.app

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class SeriesDerivedState(
    val resumeIndex: Int,
    val visible: List<Chapter>,
    val chapterDisplay: ChapterDisplay,
    val filtersActive: Boolean,
    val downloadedCount: Int,
    val anyProgress: Boolean,
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
    val progress = remember(chapters, effectiveReadTick, sourceId, isAnimeSource) {
        // Resume target and "has progress" used to scan the entire chapter list
        // independently and repeat the same SharedPreferences lookups. One
        // snapshot keeps large-series opens cheaper without changing semantics.
        seriesProgressSummary(
            context = context,
            chapters = chapters,
            sourceId = sourceId,
            isAnimeSource = isAnimeSource,
        )
    }
    val resumeIndex = progress.resumeIndex
    /**
     * What the list below draws — filtered and sorted. **Not** what anything
     * indexes: see `onOpen`.
     *
     * Keyed on both ticks because the filters read read-state and disk, so
     * finishing a chapter or a download changes which rows belong here.
     */
    val visible = remember(chapters, effectiveReadTick, downloadTick, optionsTick, sourceId) {
        visibleChapters(context, chapters, sourceId)
    }
    val chapterDisplay = remember(optionsTick) { ChapterPrefs.display(context) }
    val filtersActive = remember(optionsTick) { ChapterPrefs.anyFilterActive(context) }
    val downloadedCount = remember(chapters, downloadTick) {
        chapters.count { Downloads.isComplete(context, it.id) }
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
            SeriesIndex.record(context, sourceId, series.id, chapters)
        }
    }

    // Seeded before the list below composes, not in an effect: the LazyColumn
    // builds its state as it composes, so a position belonging to a different
    // series has to be gone by then rather than one frame later.
    scroll.sync(series.id)

    val anyProgress = progress.anyProgress

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
        listState = listState,
        barAlpha = barAlpha,
    )
}
