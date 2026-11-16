package com.mangareader.app

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterTransientRetryPolicyTest {
    private fun failed(cause: Throwable, total: Int = 10, count: Int = 2) =
        ChapterDownloadException(count, total, PageDownloadException(0, null, cause))

    @Test fun transientNetworkFailureGetsOneExtraPass() {
        val timeoutFailure = failed(SocketTimeoutException("timeout"))
        assertTrue(shouldRetryTransientChapterFailure(timeoutFailure, 0))
        assertFalse(shouldRetryTransientChapterFailure(timeoutFailure, 1))
        assertFalse(shouldRetryTransientChapterFailure(timeoutFailure, 20))
        assertFalse(shouldRetryTransientChapterFailure(timeoutFailure, -1))
        assertTrue(shouldRetryTransientChapterFailure(failed(ConnectException("refused")), 0))
        assertTrue(shouldRetryTransientChapterFailure(failed(UnknownHostException("dns")), 0))
    }

    @Test fun persistentOrMalformedFailuresDoNotRepeatChapter() {
        assertFalse(shouldRetryTransientChapterFailure(failed(IllegalArgumentException("bad URL")), 0))
        assertFalse(shouldRetryTransientChapterFailure(failed(IOException("Invalid cached image")), 0))
        assertFalse(shouldRetryTransientChapterFailure(failed(IllegalStateException("no handle"), total = 0), 0))
        assertFalse(shouldRetryTransientChapterFailure(failed(SocketTimeoutException(), count = 0), 0))
        assertFalse(shouldRetryTransientChapterFailure(IllegalStateException("source removed"), 0))
        assertFalse(shouldRetryTransientChapterFailure(ChapterDownloadException(2, 10), 0))
    }
}
