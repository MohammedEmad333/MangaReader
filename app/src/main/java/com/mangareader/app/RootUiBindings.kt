package com.mangareader.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

internal data class RootUiBindings(
    val libraryCategory: String?,
    val onLibraryCategoryChange: (String?) -> Unit,
    val librarySearch: String,
    val onLibrarySearchChange: (String) -> Unit,
    val librarySearchOpen: Boolean,
    val onLibrarySearchOpenChange: (Boolean) -> Unit,
    val libraryScroll: ScrollMemory,
    val browseScroll: ScrollMemory,
    val seriesScroll: ScrollMemory,
    val sourcesScroll: ScrollMemory,
    val extensionsScroll: ScrollMemory,
    val historyScroll: ScrollMemory,
    val downloadsScroll: ScrollMemory,
    val globalSearchScroll: ScrollMemory,
    val releaseNotes: List<ReleaseNote>,
    val whatsNewOpen: Boolean,
    val onDismissWhatsNew: () -> Unit
)

@Composable
internal fun rememberRootUiBindings(
    context: Context,
    initialLibraryCategory: String?,
    releaseNotes: List<ReleaseNote>,
): RootUiBindings {
    // These values were already loaded by StartupUiSnapshot on IO. Starting
    // from them preserves the exact old restore behavior without making the
    // first composition pay for SharedPreferences parsing.
    var libraryCategory by rememberSaveable {
        mutableStateOf(initialLibraryCategory)
    }
    var librarySearch by rememberSaveable {
        mutableStateOf("")
    }
    var librarySearchOpen by rememberSaveable {
        mutableStateOf(false)
    }

    val libraryScroll = remember { ScrollMemory() }
    val browseScroll = remember { ScrollMemory() }
    val seriesScroll = remember { ScrollMemory() }
    val sourcesScroll = remember { ScrollMemory() }
    val extensionsScroll = remember { ScrollMemory() }
    val historyScroll = remember { ScrollMemory() }
    val downloadsScroll = remember { ScrollMemory() }
    val globalSearchScroll = remember { ScrollMemory() }

    var whatsNewOpen by remember {
        mutableStateOf(releaseNotes.isNotEmpty())
    }

    LaunchedEffect(releaseNotes) {
        if (releaseNotes.isEmpty()) {
            WhatsNew.markSeen(context)
        }
    }

    return RootUiBindings(
        libraryCategory = libraryCategory,
        onLibraryCategoryChange = {
            libraryCategory = it
        },
        librarySearch = librarySearch,
        onLibrarySearchChange = {
            librarySearch = it
        },
        librarySearchOpen = librarySearchOpen,
        onLibrarySearchOpenChange = {
            librarySearchOpen = it
        },
        libraryScroll = libraryScroll,
        browseScroll = browseScroll,
        seriesScroll = seriesScroll,
        sourcesScroll = sourcesScroll,
        extensionsScroll = extensionsScroll,
        historyScroll = historyScroll,
        downloadsScroll = downloadsScroll,
        globalSearchScroll = globalSearchScroll,
        releaseNotes = releaseNotes,
        whatsNewOpen = whatsNewOpen,
        onDismissWhatsNew = {
            whatsNewOpen = false
            WhatsNew.markSeen(context)
        }
    )
}
