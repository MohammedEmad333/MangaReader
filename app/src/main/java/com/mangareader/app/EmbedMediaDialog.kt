package com.mangareader.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun EmbedMediaDialog(
    media: List<String>,
    onDismiss: () -> Unit,
    onOpenVideo: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video link") },
        text = {
            if (media.isEmpty()) {
                Text(
                    "Nothing usable found. The player may be feeding itself " +
                        "from JavaScript, in which case there is no address " +
                        "an outside app could open."
                )
            } else {
                Column {
                    Text("Opens in Yomu’s built-in video player.")
                    Spacer(Modifier.height(12.dp))
                    media.take(6).forEach { link ->
                        TextButton(
                            onClick = { onOpenVideo(link) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                link.substringAfterLast('/').take(48).ifBlank { link.take(48) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
