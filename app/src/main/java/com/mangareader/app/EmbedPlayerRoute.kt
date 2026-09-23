package com.mangareader.app

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun EmbedPlayerRoute(
    embed: Pair<String, String>,
    media: List<String>?,
    onMediaFound: (List<String>) -> Unit,
    onDismissMedia: () -> Unit,
    onBack: () -> Unit,
    onPlayerError: (String) -> Unit
) {
    val context = LocalContext.current

    EmbedWebViewScreen(
        url = embed.first,
        referer = embed.second,
        onMediaFound = onMediaFound,
        onBack = onBack
    )

    if (media != null) {
        EmbedMediaDialog(
            media = media,
            onDismiss = onDismissMedia,
            onOpenVideo = { link ->
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(link), "video/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (embed.second.isNotBlank()) {
                        putExtra("headers", arrayOf("Referer", embed.second))
                    }
                }
                runCatching { context.startActivity(view) }
                    .onFailure {
                        onPlayerError("No installed app can play that link")
                    }
            }
        )
    }
}
