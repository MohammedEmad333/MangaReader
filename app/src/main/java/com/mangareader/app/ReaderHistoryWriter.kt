package com.mangareader.app

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Serial writer for reader-history updates.
 *
 * A settled reader page used to call History.touch() directly from Compose's
 * onProgress callback. That reparses/serializes the capped history and schedules
 * a SharedPreferences write on the UI thread for every page turn. Keep page/read
 * flags synchronous — they are tiny scalar edits that define resume correctness
 * — but move the heavier history upsert onto one process-lifetime IO worker.
 *
 * One channel, rather than one coroutine per page, preserves update order. The
 * flush barrier lets the close path wait for everything queued before it before
 * refreshing the History tab's in-memory list.
 */
internal object ReaderHistoryWriter {
    private sealed interface Message {
        data class Write(
            val context: Context,
            val entry: HistoryEntry,
        ) : Message

        data class Flush(
            val done: CompletableDeferred<Unit>,
        ) : Message
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messages = Channel<Message>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (message in messages) {
                when (message) {
                    is Message.Write -> History.touch(message.context, message.entry)
                    is Message.Flush -> message.done.complete(Unit)
                }
            }
        }
    }

    fun submit(context: Context, entry: HistoryEntry) {
        messages.trySend(
            Message.Write(
                context = context.applicationContext,
                entry = entry,
            )
        )
    }

    suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        messages.send(Message.Flush(done))
        done.await()
    }
}
