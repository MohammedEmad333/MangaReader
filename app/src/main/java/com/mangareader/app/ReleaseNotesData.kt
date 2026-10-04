package com.mangareader.app

/**
 * Immutable release-note data, newest first.
 *
 * Split into bounded data chunks so changelog maintenance does not keep growing
 * one monolithic Kotlin source file. Filtering/query behavior belongs in Changelog.
 */
internal val releaseNotes: List<ReleaseNote> =
    releaseNotes271To271 +
    releaseNotes258To258 +
    releaseNotes209To183 +
    releaseNotes182To156 +
    releaseNotes155To119 +
    releaseNotes118To92 +
    releaseNotes91To79 +
    releaseNotes78To67 +
    releaseNotes65To52
