package com.mangareader.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage

@Composable
internal fun GenreChips(
    genres: List<String>,
    sourceName: String,
    onSearchTag: (String) -> Unit,
    onLibrarySearchTag: (String) -> Unit,
    onGlobalSearchTag: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    genres.forEach { genre ->
        Box {
            var tagMenu by remember(genre) { mutableStateOf(false) }
            SuggestionChip(
                onClick = { tagMenu = true },
                label = { Text(genre) },
            )
            DropdownMenu(
                expanded = tagMenu,
                onDismissRequest = { tagMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Search $sourceName") },
                    onClick = {
                        tagMenu = false
                        onSearchTag(genre)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Search library") },
                    onClick = {
                        tagMenu = false
                        onLibrarySearchTag(genre)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Global search") },
                    onClick = {
                        tagMenu = false
                        onGlobalSearchTag(genre)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Copy to clipboard") },
                    onClick = {
                        tagMenu = false
                        clipboard.setText(AnnotatedString(genre))
                    },
                )
            }
        }
    }
}

@Composable
internal fun CoverViewer(
    cover: Any?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f)),
        ) {
            ZoomableAsyncImage(
                model = cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onClick = { _ -> onDismiss() },
            )
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                BackButton(onDismiss)
            }
        }
    }
}
