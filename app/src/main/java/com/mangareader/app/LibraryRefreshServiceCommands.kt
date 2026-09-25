package com.mangareader.app

import android.content.Context
import android.content.Intent
import android.os.Build

internal object LibraryRefreshServiceCommands {
    fun start(context: Context) = start(context, null, "")

    fun startUncounted(context: Context, count: Int) {
        start(context, null, "$count never counted", uncounted = true)
    }

    fun start(
        context: Context,
        sourceIds: Set<String>?,
        scopeLabel: String,
        uncounted: Boolean = false,
    ) {
        LibraryRefresh.clearSummary(context)
        val intent = Intent(context, LibraryRefreshService::class.java)
        if (!sourceIds.isNullOrEmpty()) {
            intent.putStringArrayListExtra(
                LibraryRefreshService.EXTRA_SOURCES,
                ArrayList(sourceIds),
            )
        }
        if (uncounted) {
            intent.putExtra(LibraryRefreshService.EXTRA_UNCOUNTED, true)
        }
        if (!sourceIds.isNullOrEmpty() || uncounted) {
            intent.putExtra(LibraryRefreshService.EXTRA_SCOPE_LABEL, scopeLabel)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun startOver(context: Context) {
        RefreshCursor.clear(context)
        start(context)
    }

    fun stop(context: Context) {
        val intent = Intent(context, LibraryRefreshService::class.java)
            .setAction(LibraryRefreshService.ACTION_STOP)
        runCatching { context.startService(intent) }
    }
}
