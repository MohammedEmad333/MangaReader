package com.mangareader.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import eu.kanade.tachiyomi.source.online.HttpSource

/**
 * A bottom-nav label that can't wrap.
 *
 * A fifth of the screen fits four of these words and not "Downloads", which
 * broke onto a second line and left an orphaned "s" under the icon. Wrapping is
 * never the right answer in a fixed-height bar, so every label is pinned to one
 * line and the longest is allowed to shrink instead.
 */
@Composable
internal fun NavLabel(text: String) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall
    )
}

/**
 * The source's website, for opening in a WebView. Null when there isn't one:
 * local folder sources, and any extension that isn't an [HttpSource].
 */
internal fun Source.siteUrl(): String? =
    ((this as? TachiyomiSourceAdapter)?.catalogueSource as? HttpSource)
        ?.baseUrl
        ?.takeIf { it.isNotBlank() }
