package com.mangareader.app

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
                runCatching {
                    context.startActivity(
                        VideoPlayerActivity.intent(
                            context = context,
                            url = link,
                            referer = embed.second,
                        ),
                    )
                }.onFailure {
                    onPlayerError("Unable to open the built-in video player")
                }
            }
        )
    }
}
