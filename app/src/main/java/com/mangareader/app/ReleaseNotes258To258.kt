package com.mangareader.app

internal val releaseNotes258To258 = listOf(
    ReleaseNote(
        code = 269,
        name = "0.269",
        header = "Duplicate-result crash fixes",
        body = "Yomu now safely removes duplicate extension packages and duplicate series returned by sources before rendering lazy lists and grids. This fixes Compose key crashes seen in Extensions, Browse, and Global Search when a repository or source returns the same item more than once.",
    ),
    ReleaseNote(
        code = 268,
        name = "0.268",
        header = "Search memory and build compatibility",
        body = "Global Search now remembers the Has results only filter across restarts. The Android build stack and compatible AndroidX dependencies were also updated so newer WorkManager and platform libraries build cleanly on CI.",
    ),
    ReleaseNote(
        code = 267,
        name = "0.267",
        header = "WebView recovery for empty sources",
        body = "Remote anime sources that return an empty catalog now offer Open source in WebView. This covers managed/browser challenges that can return HTTP 200 instead of an obvious 403, including sources like Cimaleek. After the challenge is completed, Yomu automatically retries the source with the shared cookies and matching User-Agent.",
    ),
    ReleaseNote(
        code = 266,
        name = "0.266",
        header = "Faster embedded-player detection",
        body = "Embedded players now expose media requests directly from WebView network interception as soon as an MP4, WebM, HLS, or DASH request appears. DOM polling remains as a fallback, so compatible players can hand off streams much sooner instead of waiting through the full scan window.",
    ),
    ReleaseNote(
        code = 265,
        name = "0.265",
        header = "Faster streams and catalog fixes",
        body = "WatanFlix and Cima4u catalog parsing now matches their current page structure. Anime stream loading returns direct streams immediately and caps slow mirror/fallback waits so the stream chooser no longer hangs for tens of seconds.",
    ),
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
