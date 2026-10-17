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
    onOpenQueue: () -> Unit,
    onOpenUpdates: () -> Unit,
) {
    fun consumeNavigationRequest(intent: Intent?) {
        if (intent == null) return
        val openQueue = intent.getBooleanExtra(DownloadService.EXTRA_OPEN_QUEUE, false)
        val openUpdates = intent.getBooleanExtra(ChapterUpdateNotifier.EXTRA_OPEN_UPDATES, false)
        val destination = notificationDestination(openQueue, openUpdates)
        // Consume one-shot navigation before the composition can be recreated.
        intent.removeExtra(DownloadService.EXTRA_OPEN_QUEUE)
        intent.removeExtra(ChapterUpdateNotifier.EXTRA_OPEN_UPDATES)
        when (destination) {
            3 -> onOpenQueue()
            5 -> onOpenUpdates()
        }
    }

    LaunchedEffect(Unit) {
        consumeNavigationRequest(activity?.intent)
    }

    DisposableEffect(activity) {
        val listener = Consumer<Intent> { consumeNavigationRequest(it) }
        activity?.addOnNewIntentListener(listener)
        onDispose {
            activity?.removeOnNewIntentListener(listener)
        }
    }
}

/** Updates win only if both flags are present; normal launches do not navigate. */
internal fun notificationDestination(openQueue: Boolean, openUpdates: Boolean): Int? = when {
    openUpdates -> 5
    openQueue -> 3
    else -> null
}
