package com.mangareader.app

/** One back priority shared by the reader's toolbar and Android system back. */
internal enum class ReaderBackTarget { SETTINGS, CHAPTERS, CONTROLS, READER }

internal fun readerBackTarget(
    settingsOpen: Boolean,
    chaptersOpen: Boolean,
    controlsVisible: Boolean,
): ReaderBackTarget = when {
    settingsOpen -> ReaderBackTarget.SETTINGS
    chaptersOpen -> ReaderBackTarget.CHAPTERS
    controlsVisible -> ReaderBackTarget.CONTROLS
    else -> ReaderBackTarget.READER
}
