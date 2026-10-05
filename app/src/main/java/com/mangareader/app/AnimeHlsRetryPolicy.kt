package com.mangareader.app

/**
 * WorkManager starts with runAttemptCount == 0. Allow two retries after the
 * initial run so temporary disconnects do not force the user to restart a large
 * segmented download manually, while still guaranteeing a terminal failure.
 */
internal fun shouldRetryHlsDownload(runAttemptCount: Int): Boolean = runAttemptCount < 2
