package com.mangareader.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Serial history persistence. Reader progress and checkpoint still save
 * immediately; the heavier history JSON is written on the IO dispatcher.
 *
 * Adjacent queued writes are coalesced by series and persisted together.
 * A flush message is a strict barrier: writes after it cannot enter its batch.
 * Batches are capped so a busy producer cannot monopolize the IO worker.
 */
internal object ReaderHistoryWriter {
    private const val MAX_BATCH_IDENTITIES = 40

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
            var errorSinceFlush: Throwable? = null
            for (message in messages) {
                when (message) {
                    is Message.Flush -> {
                        val error = errorSinceFlush
                        errorSinceFlush = null
                        if (error == null) message.done.complete(Unit)
                        else message.done.completeExceptionally(error)
                    }
                    is Message.Write -> {
                        val batch = ReaderHistoryBatch()
                        batch.add(message.entry)
                        var barrier: Message.Flush? = null
                        while (batch.size < MAX_BATCH_IDENTITIES) {
                            val next = messages.tryReceive().getOrNull() ?: break
                            when (next) {
                                is Message.Write -> batch.add(next.entry)
                                is Message.Flush -> {
                                    barrier = next
                                    break
                                }
                            }
                        }
                        try {
                            History.touchAll(message.context, batch.entries())
                        } catch (error: Exception) {
                            // Keep the worker alive so a failure cannot strand future flushes.
                            Log.e("ReaderHistoryWriter", "Could not save reading history", error)
                            if (errorSinceFlush == null) errorSinceFlush = error
                        }
                        barrier?.let { flush ->
                            val error = errorSinceFlush
                            errorSinceFlush = null
                            if (error == null) flush.done.complete(Unit)
                            else flush.done.completeExceptionally(error)
                        }
                    }
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
