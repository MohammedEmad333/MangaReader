package com.mangareader.app

internal fun String.isMangaExtensionSourceId(): Boolean =
    startsWith("tachi:")

internal fun String.isAnimeExtensionSourceId(): Boolean =
    startsWith("aniyomi:")

internal fun String.isExtensionSourceId(): Boolean =
    isMangaExtensionSourceId() || isAnimeExtensionSourceId()

internal fun String.isLocalSourceId(): Boolean =
    !isExtensionSourceId()
