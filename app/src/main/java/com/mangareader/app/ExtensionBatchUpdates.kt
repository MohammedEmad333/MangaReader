package com.mangareader.app

/** Only offer bulk updates for installed extensions with newer catalogue versions. */
internal fun extensionsEligibleForBulkUpdate(visibleExtensions: List<Extension>): List<Extension> =
    visibleExtensions.filter { it.hasUpdate }.distinctBy { it.pkgName }

internal enum class ExtensionBatchPhase { DOWNLOADING, INSTALLING }

internal data class ExtensionBatchFailure(val name: String, val reason: String)

/**
 * A completed step means the Android installer has returned and the installed
 * package version has been checked. Opening the installer alone is not success.
 */
internal data class ExtensionBatchProgress(
    val total: Int,
    val attempted: Int = 0,
    val updated: Int = 0,
    val failures: List<ExtensionBatchFailure> = emptyList(),
    val currentName: String? = null,
    val phase: ExtensionBatchPhase? = null,
    val cancelRequested: Boolean = false,
    val finished: Boolean = false,
) {
    val remaining: Int get() = total - attempted
    val skipped: Int get() = if (finished && cancelRequested) remaining else 0

    fun start(name: String, stage: ExtensionBatchPhase) =
        copy(currentName = name, phase = stage)

    fun record(name: String, success: Boolean, reason: String = ""): ExtensionBatchProgress {
        require(attempted < total)
        return copy(
            attempted = attempted + 1,
            updated = updated + if (success) 1 else 0,
            failures = if (success) failures else failures + ExtensionBatchFailure(name, reason),
            currentName = null,
            phase = null,
        )
    }

    fun cancelRemaining(): ExtensionBatchProgress = copy(cancelRequested = true)

    fun finish(): ExtensionBatchProgress =
        copy(currentName = null, phase = null, finished = true)
}
