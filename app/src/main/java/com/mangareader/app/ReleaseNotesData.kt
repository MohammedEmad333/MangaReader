package com.mangareader.app

/**
 * Immutable release-note data, newest first.
 *
 * Split into bounded data chunks so changelog maintenance does not keep growing
 * one monolithic Kotlin source file. Filtering/query behavior belongs in Changelog.
 */
internal val releaseNotes: List<ReleaseNote> =
    releaseNotes286To286 +
    releaseNotes285To285 +
    releaseNotes284To284 +
    releaseNotes283To283 +
    releaseNotes282To282 +
    releaseNotes281To281 +
    releaseNotes280To280 +
    releaseNotes279To279 +
    releaseNotes278To278 +
    releaseNotes277To277 +
    releaseNotes276To276 +
    releaseNotes275To275 +
    releaseNotes274To274 +
    releaseNotes273To273 +
    releaseNotes272To272 +
    releaseNotes258To258 +
    releaseNotes209To183 +
    releaseNotes182To156 +
    releaseNotes155To119 +
    releaseNotes118To92 +
    releaseNotes91To79 +
    releaseNotes78To67 +
    releaseNotes65To52
