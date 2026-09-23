package com.mangareader.app

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
