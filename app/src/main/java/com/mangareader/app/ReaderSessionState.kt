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

    fun open(
        context: Context,
        scope: CoroutineScope,
        source: Source,
        chapters: List<Chapter>,
        index: Int,
        onRootLoadingChanged: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val chapter = chapters.getOrNull(index) ?: return
        onError("")
        loadJob?.cancel()

        val resumeAt = savedPage(
            context,
            chapterKeyOf(source.id, chapter)
        )
        val token = ++loadSequence
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
                        pages = partial
                        if (!opened && partial.isNotEmpty()) {
                            opened = true
                            chapterIndex = index
                        }
                    }
                }

                if (pages.isEmpty()) {
                    onError("This chapter has no pages")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                onError(
                    sourceFailureMessage(
                        error,
                        "Could not open this chapter"
                    )
                )
                pages = emptyList()
            } finally {
                if (token == loadSequence) {
                    loading = false
                    onRootLoadingChanged(false)
                }
            }
        }
    }

    fun close() {
        loadJob?.cancel()
        loadJob = null
        loadSequence++
        loading = false
        chapterIndex = null
        pages = emptyList()
    }
}
