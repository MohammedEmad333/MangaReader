package com.mangareader.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ChapterVideoDialog(
    scanning: Boolean,
    scan: VideoScan?,
    isAnime: Boolean,
    onDismiss: () -> Unit,
    onOpenEmbed: (String) -> Unit,
    onOpenVideo: (PlayableVideo) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isAnime) "Streams for this episode" else "Videos in this chapter") },
        text = {
            when {
                scanning -> Text(if (isAnime) "Loading episode streams…" else "Scanning the chapter's page…")
                scan == null || (scan.videos.isEmpty() && scan.embeds.isEmpty()) -> Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        if (isAnime) "No playable stream was returned for this episode."
                        else "No playable video found on this chapter's page."
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        scan?.note ?: "The scan returned nothing at all.",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                scan.videos.isEmpty() -> Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    val plural = if (scan.embeds.size == 1) "player" else "players"
                    Text("No direct video file, but this page embeds ${scan.embeds.size} $plural.")
                    Spacer(Modifier.height(12.dp))
                    scan.embeds.forEachIndexed { index, url ->
                        TextButton(
                            onClick = { onOpenEmbed(url) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Open player ${index + 1}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        scan.note ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                else -> Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(if (isAnime) "Choose a stream to start watching." else "Tap one to open it in a video player.")
                    Spacer(Modifier.height(12.dp))
                    scan.videos.forEachIndexed { index, video ->
                        TextButton(
                            onClick = { onOpenVideo(video) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                video.title.ifBlank { "Video ${index + 1}" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
