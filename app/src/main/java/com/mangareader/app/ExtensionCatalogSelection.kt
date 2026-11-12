package com.mangareader.app

/**
 * Combine extension catalogues by package ID while keeping the first repository's
 * position. A later repository can provide a newer version of the same extension:
 * hiding it with distinctBy(pkgName) would suppress the Update button.
 */
internal fun selectLatestExtensions(extensions: List<Extension>): List<Extension> {
    val selected = LinkedHashMap<String, Extension>()
    for (candidate in extensions) {
        val previous = selected[candidate.pkgName]
        if (previous == null) {
            selected[candidate.pkgName] = candidate
            continue
        }

        val preferred = if (compareVersions(candidate.versionName, previous.versionName) > 0) {
            candidate
        } else {
            previous
        }
        // An extension marked as adult content by any configured catalogue must
        // not become visible merely because a second catalogue omits the flag.
        selected[candidate.pkgName] = if (previous.isNsfw || candidate.isNsfw) {
            preferred.copy(isNsfw = true)
        } else {
            preferred
        }
    }
    return selected.values.toList()
}
