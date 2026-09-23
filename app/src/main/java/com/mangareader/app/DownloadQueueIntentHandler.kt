package com.mangareader.app

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.core.util.Consumer

@Composable
internal fun DownloadQueueIntentHandler(
    activity: ComponentActivity?,
    onOpenQueue: () -> Unit
) {
    fun consumeQueueRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(DownloadService.EXTRA_OPEN_QUEUE, false) != true) return
        intent.removeExtra(DownloadService.EXTRA_OPEN_QUEUE)
        onOpenQueue()
    }

    LaunchedEffect(Unit) {
        consumeQueueRequest(activity?.intent)
    }

    DisposableEffect(activity) {
        val listener = Consumer<Intent> { consumeQueueRequest(it) }
        activity?.addOnNewIntentListener(listener)
        onDispose {
            activity?.removeOnNewIntentListener(listener)
        }
    }
}
