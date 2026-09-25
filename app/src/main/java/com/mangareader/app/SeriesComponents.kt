package com.mangareader.app

import androidx.compose.ui.unit.dp

/** Adds or removes one id. Written out because Set has no toggle. */
internal fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

/**
 * How far a read chapter's text fades.
 *
 * This is the only visual signal that a chapter has been read, so it must stay
 * visible even in bright conditions.
 */
internal const val READ_DIM = 0.45f

/**
 * Height of the series screen's top bar, reserved in the scrolling header.
 */
internal val TOP_BAR_HEIGHT = 64.dp

/** How far the header scrolls before the top bar is fully opaque. */
internal val TOP_BAR_FADE_OVER = 120.dp
