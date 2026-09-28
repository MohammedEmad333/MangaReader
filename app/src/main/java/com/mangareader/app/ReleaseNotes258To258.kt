package com.mangareader.app

internal val releaseNotes258To258 = listOf(
    ReleaseNote(
        code = 264,
        name = "0.264",
        header = "More SeriesHub host extractors",
        body = "SeriesHub now recognizes OK.ru, StreamWish-family hosts, and MixDrop in addition to StreamTape, DoodStream, VidMoly, and VOE. Known hosts are resolved before generic iframe/media fallback.",
    ),
    ReleaseNote(
        code = 263,
        name = "0.263",
        header = "Host-specific video extractors",
        body = "SeriesHub sources now detect common video hosts and use dedicated extraction paths for StreamTape, DoodStream, VidMoly, and VOE before falling back to generic iframe/media parsing. This follows the extractor-first architecture used by mature Aniyomi repositories.",
    ),
    ReleaseNote(
        code = 262,
        name = "0.262",
        header = "Anime source diagnostics and cleaner episodes",
        body = "Connection probe now works with Aniyomi HTTP sources such as Cima4u, DramaCafe, and WatanFlix. DramaCafe also stops treating unrelated watch links as episodes, and movies now open as one playable item.",
    ),
    ReleaseNote(
        code = 261,
        name = "0.261",
        header = "WatanFlix joins SeriesHub sources",
        body = "Adds WatanFlix as an installable SeriesHub video extension with catalogue, search, series details, episode discovery, hoster discovery, and stream handoff.",
    ),
    ReleaseNote(
        code = 260,
        name = "0.260",
        header = "DramaCafe joins SeriesHub sources",
        body = "Adds an installable DramaCafe anime/video extension with catalogue, search, details, episode discovery, hoster discovery, and playable stream extraction.",
    ),
    ReleaseNote(
        code = 259,
        name = "0.259",
        header = "Cima4u catalogue now shows real titles",
        body = "Cima4u now filters out navigation and category links from Popular/Latest and only keeps real media cards with artwork, fixing the category tiles and blank covers seen in 0.258.",
    ),
    ReleaseNote(
        code = 258,
        name = "0.258",
        header = "First SeriesHub video extension",
        body = "Adds an installable Aniyomi-compatible Cima4u extension module. " +
            "Yomu can load it as an anime/video source, browse titles and episodes, " +
            "discover public hosters, resolve playable streams, and hand them to the built-in player.",
    ),
)
