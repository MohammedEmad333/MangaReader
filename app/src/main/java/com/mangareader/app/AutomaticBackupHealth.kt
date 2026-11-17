package com.mangareader.app

/** Pure projection of recorded automatic backup attempts, never an uptime check. */
internal data class AutomaticBackupHealth(
    val status: String,
    val retryRecommended: Boolean,
)

internal fun automaticBackupHealth(
    lastSuccessAt: Long,
    lastAttemptAt: Long,
    lastAttemptSucceeded: Boolean,
): AutomaticBackupHealth = when {
    lastAttemptAt > 0L && !lastAttemptSucceeded ->
        AutomaticBackupHealth(
            status = if (lastSuccessAt > 0L)
                "Last attempt failed. The last successful backup was kept. Check your storage location, then try again."
            else
                "The last attempt failed and no successful backup has been recorded. Check your storage location, then try again.",
            retryRecommended = true,
        )
    lastSuccessAt > 0L ->
        AutomaticBackupHealth("Last automatic backup succeeded.", retryRecommended = false)
    else ->
        AutomaticBackupHealth("No successful automatic backup recorded.", retryRecommended = false)
}
