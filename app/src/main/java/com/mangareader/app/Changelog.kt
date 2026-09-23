package com.mangareader.app

data class ReleaseNote(
    val code: Int,
    val name: String,
    val header: String,
    val body: String
)

/**
 * Query API for the bundled release history.
 *
 * Add an entry to ReleaseNotesData.kt in the same commit that bumps versionCode.
 */
object Changelog {
    val notes: List<ReleaseNote> = releaseNotes

    /**
     * Notes for everything newer than [installed], up to and including [current].
     *
     * Half-open at the bottom: the version already on the phone has been seen.
     */
    fun since(installed: Int, current: Int): List<ReleaseNote> =
        notes.filter { it.code in (installed + 1)..current }
            .sortedByDescending { it.code }
}
