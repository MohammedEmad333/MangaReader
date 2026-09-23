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
    val releaseNotes: List<ReleaseNote>,
    val whatsNewOpen: Boolean,
    val onDismissWhatsNew: () -> Unit
)

@Composable
internal fun rememberRootUiBindings(
    context: Context
): RootUiBindings {
    var libraryCategory by rememberSaveable {
        mutableStateOf<String?>(
            LibraryPrefs.lastCategory(context)
        )
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

    val releaseNotes = remember {
        WhatsNew.pending(context)
    }
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
        releaseNotes = releaseNotes,
        whatsNewOpen = whatsNewOpen,
        onDismissWhatsNew = {
            whatsNewOpen = false
            WhatsNew.markSeen(context)
        }
    )
}
