package com.mangareader.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun BrowseEmptyState(
    loading: Boolean,
    error: String?,
    query: String,
    isLocalSource: Boolean,
    onDiagnose: () -> Unit,
    onOpenWebView: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (loading) return@Box

        when {
            error != null -> Text(
                "Nothing found in this source.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            query.isNotBlank() -> Text(
                "No results for “$query”.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            isLocalSource -> Text(
                "Nothing found in this source.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                Text(
                    "This source returned no results.",
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "The request succeeded but no titles could be read from the page. " +
                        "The site may have changed, or it may have returned a browser/Cloudflare " +
                        "challenge with HTTP 200. You can open the source in WebView first, then retry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                if (onOpenWebView != null) {
                    TextButton(onClick = onOpenWebView) {
                        Text("Open source in WebView")
                    }
                }
                TextButton(onClick = onDiagnose) {
                    Text("Connection probe")
                }
            }
        }
    }
}
