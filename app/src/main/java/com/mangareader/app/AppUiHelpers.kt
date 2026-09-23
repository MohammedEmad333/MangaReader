package com.mangareader.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import eu.kanade.tachiyomi.source.online.HttpSource

@Composable
internal fun NavLabel(text: String) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall
    )
}

internal fun Source.siteUrl(): String? =
    ((this as? TachiyomiSourceAdapter)?.catalogueSource as? HttpSource)
        ?.baseUrl
        ?.takeIf { it.isNotBlank() }
