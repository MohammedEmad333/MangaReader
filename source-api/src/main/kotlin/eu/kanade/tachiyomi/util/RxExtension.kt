package eu.kanade.tachiyomi.util

import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Observable
import rx.Subscriber
import rx.Subscription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Replacement for Tachiyomi's expect/actual RxExtension, which lived in the KMP
 * source sets and imported awaitSingle from :core. Self-contained here.
 *
 * HttpSource and CatalogueSource call this to adapt the RxJava 1.x
 * fetch* methods that lib-1.4 extensions override into suspend functions.
 */
suspend fun <T> Observable<T>.awaitSingle(): T = suspendCancellableCoroutine { continuation ->
    val subscription: Subscription = this.single().subscribe(
        object : Subscriber<T>() {
            override fun onNext(value: T) {
                if (continuation.isActive) continuation.resume(value)
            }

            override fun onCompleted() {
                // single() guarantees onNext fired first; nothing to do.
            }

            override fun onError(error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        },
    )
    continuation.invokeOnCancellation { subscription.unsubscribe() }
}
