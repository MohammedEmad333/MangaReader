package com.mangareader.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The one back affordance in the app. */
@Composable
internal fun BackButton(onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick) {
        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
    }
}

/** Section label above a run of source/extension rows. */
@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = 22.dp,
            bottom = 8.dp,
        ),
    )
}

/** Plain-language gloss for HTTP codes that mean something actionable. */
internal fun httpHint(error: String): String? {
    val code = Regex("""HTTP error (\d{3})""")
        .find(error)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: return null

    return when (code) {
        429 ->
            "Too many requests — the source is rate-limiting. Wait a minute, then retry."

        500, 502, 503 ->
            "The source’s server is erroring or overloaded. Nothing to fix here; try later."

        504, 520, 521, 522, 523, 524 ->
            "Cloudflare couldn’t reach the source’s own server. The site is down, not " +
                "the app — wait, or use another source."

        else -> null
    }
}

/** The 18+ marker shown next to adult sources, matching the extension index flag. */
@Composable
internal fun NsfwBadge() {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = "18+",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Shared error surface under screen top bars. */
@Composable
internal fun ErrorBanner(
    error: String?,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    if (error == null) return

    Surface(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
            )

            val hint = httpHint(error)
            if (hint != null) {
                Text(
                    text = hint,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }

            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
