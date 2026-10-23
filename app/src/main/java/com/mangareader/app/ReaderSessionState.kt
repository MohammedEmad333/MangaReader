package com.mangareader.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal class ReaderSessionState {
    var chapterIndex by mutableStateOf<Int?>(null)
        private set
    var pages by mutableStateOf<List<File?>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set

    private var loadJob: Job? = null
    private var loadSequence = 0
    private var rootLoadingCallback: ((Boolean) -> Unit)? = null

    fun open(
        context: Context,
        scope: CoroutineScope,
        source: Source,
        chapters: List<Chapter>,
        index: Int,
        onRootLoadingChanged: (Boolean) -> Unit,
        onClearError: () -> Unit,
        onError: (String) -> Unit,
        retryAt: Int? = null,
    ) {
        val chapter = chapters.getOrNull(index) ?: return
        if (retryAt != null && !canRetryReaderPage(pages, loading, chapterIndex, index, retryAt)) return
        val previousPages = if (retryAt != null) pages else emptyList()
        onClearError()
        loadJob?.cancel()
        // Release the previous screen's loading indicator before replacing its callback.
        rootLoadingCallback?.invoke(false)
        rootLoadingCallback = null

        val resumeAt = retryAt ?: savedPage(
            context,
            chapterKeyOf(source.id, chapter)
        )
        val token = ++loadSequence
        rootLoadingCallback = onRootLoadingChanged
        if (retryAt == null) {
            // Never display the previous series/chapter while a new one loads.
            pages = emptyList()
            chapterIndex = null
        }
        var opened = false

        loadJob = scope.launch {
            loading = true
            onRootLoadingChanged(true)
            try {
                streamChapterPages(
                    source = source,
                    chapter = chapter,
                    resumeAt = resumeAt
                ) { partial ->
                    withContext(Dispatchers.Main) {
                        if (token != loadSequence) return@withContext
                        pages = if (retryAt == null) partial else mergeRetriedReaderPages(previousPages, partial)
                        if (!opened && partial.isNotEmpty()) {
                            opened = true
                            chapterIndex = index
                        }
                    }
                }

                if (token == loadSequence && pages.isEmpty()) {
                    // A completed empty stream must not leave a chapter selected.
                    if (retryAt == null) chapterIndex = null
                    onError("This chapter has no pages")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (token == loadSequence) {
                    onError(
                        sourceFailureMessage(
                            error,
                            "Could not open this chapter"
                        )
                    )
                    // Preserve successfully streamed pages on a network failure.
                    // The reader can still display them while reporting the error.
                    if (pages.isEmpty()) {
                        pages = previousPages
                        if (retryAt == null) chapterIndex = null
                    }
                }
            } finally {
                if (token == loadSequence) {
                    loading = false
                    onRootLoadingChanged(false)
                    rootLoadingCallback = null
                    // Do not retain a completed chapter streaming job.
                    loadJob = null
                }
            }
        }
    }

    fun close() {
        loadJob?.cancel()
        loadJob = null
        loadSequence++
        rootLoadingCallback?.invoke(false)
        rootLoadingCallback = null
        loading = false
        chapterIndex = null
        pages = emptyList()
    }
}
