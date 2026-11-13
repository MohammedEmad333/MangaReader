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
    private const val MAX_BATCH_UPDATES = 80

    private sealed interface Message {
        data class Write(
            val context: Context,
            val entry: HistoryEntry,
        ) : Message

        data class Flush(
            val done: CompletableDeferred<Unit>,
        ) : Message
        data class Remove(
            val context: Context,
            val chapterKey: String,
            val done: CompletableDeferred<List<HistoryEntry>>,
        ) : Message
        data class Clear(
            val context: Context,
            val done: CompletableDeferred<List<HistoryEntry>>,
        ) : Message
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messages = Channel<Message>(Channel.UNLIMITED)

    init {
        scope.launch {
            var errorSinceFlush: Throwable? = null

            // A mutation is also a strict FIFO barrier. A queued remove/clear
            // must run after all earlier reads, never before pending reader saves.
            fun applyBarrier(message: Message) {
                when (message) {
                    is Message.Flush -> {
                        val error = errorSinceFlush
                        errorSinceFlush = null
                        if (error == null) message.done.complete(Unit)
                        else message.done.completeExceptionally(error)
                    }
                    is Message.Remove -> {
                        try {
                            History.remove(message.context, message.chapterKey)
                            message.done.complete(History.forDisplay(message.context))
                        } catch (error: Exception) {
                            message.done.completeExceptionally(error)
                        }
                    }
                    is Message.Clear -> {
                        try {
                            History.clear(message.context)
                            message.done.complete(History.forDisplay(message.context))
                        } catch (error: Exception) {
                            message.done.completeExceptionally(error)
                        }
                    }
                    is Message.Write -> error("Write is not a barrier")
                }
            }

            for (message in messages) {
                when (message) {
                    is Message.Write -> {
                        val batch = ReaderHistoryBatch()
                        batch.add(message.entry)
                        var barrier: Message? = null
                        while (batch.canCoalesceMore(MAX_BATCH_IDENTITIES, MAX_BATCH_UPDATES)) {
                            val next = messages.tryReceive().getOrNull() ?: break
                            when (next) {
                                is Message.Write -> batch.add(next.entry)
                                else -> {
                                    barrier = next
                                    break
                                }
                            }
                        }
                        try {
                            History.touchAll(message.context, batch.entries())
                        } catch (error: Exception) {
                            // Keep the worker alive so a failure cannot strand future barriers.
                            Log.e("ReaderHistoryWriter", "Could not save reading history", error)
                            if (errorSinceFlush == null) errorSinceFlush = error
                        }
                        barrier?.let(::applyBarrier)
                    }
                    else -> applyBarrier(message)
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

    /** FIFO with reader updates, so a deleted history row cannot be revived by queued writes. */
    suspend fun remove(context: Context, chapterKey: String): List<HistoryEntry> {
        val done = CompletableDeferred<List<HistoryEntry>>()
        messages.send(Message.Remove(context.applicationContext, chapterKey, done))
        return done.await()
    }

    /** Do not clear history until all previously queued reader updates have persisted. */
    suspend fun clear(context: Context): List<HistoryEntry> {
        val done = CompletableDeferred<List<HistoryEntry>>()
        messages.send(Message.Clear(context.applicationContext, done))
        return done.await()
    }
}
