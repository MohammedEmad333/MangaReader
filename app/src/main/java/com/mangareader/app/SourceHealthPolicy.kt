package com.mangareader.app

/**
 * Snapshot hints, not a claim that an extension is online. No network traffic
 * occurs during classification or when the Health Center is opened.
 */
internal data class SourceHealthEntry(
    val id: String,
    val name: String,
    val installed: Boolean,
    val failedChapters: Int,
    val hint: String,
)

internal fun sourceFailureHint(reason: String): String {
    val text = reason.lowercase()
    return when {
        "429" in text || "rate limit" in text || "too many requests" in text ->
            "Rate limited: retry later; repeated requests may make it worse."
        "403" in text || "cloudflare" in text || "access denied" in text ->
            "Access blocked: check source login or Cloudflare access."
        "404" in text || "not found" in text ->
            "Missing page or URL: the source may have moved the chapter."
        "timeout" in text || "timed out" in text ||
            "unable to resolve host" in text || "unknownhost" in text ||
            "connection" in text || "network" in text ->
            "Network problem: check connectivity and try a manual probe."
        "no longer installed" in text || "missing source" in text ->
            "Source unavailable: reinstall or restore its extension."
        else -> "Check the failure in Downloads, then run a manual probe if needed."
    }
}

internal fun buildSourceHealthEntries(
    installed: List<Pair<String, String>>,
    failed: List<Pair<String, String>>,
): List<SourceHealthEntry> {
    val names = linkedMapOf<String, String>()
    installed.forEach { (id, name) ->
        if (id.isNotBlank() && id !in names) names[id] = name.ifBlank { id }
    }
    val failures = failed.filter { it.first.isNotBlank() }.groupBy { it.first }
    val allIds = (names.keys + failures.keys).distinct()
    return allIds.map { id ->
        val reports = failures[id].orEmpty()
        SourceHealthEntry(
            id = id,
            name = names[id] ?: id,
            installed = id in names,
            failedChapters = reports.size,
            hint = when {
                id !in names -> "Source not installed; existing downloads remain unchanged."
                reports.isEmpty() -> "No recorded failed downloads. Connectivity not checked."
                else -> sourceFailureHint(reports.first().second)
            },
        )
    }.sortedWith(
        compareByDescending<SourceHealthEntry> { !it.installed }
            .thenByDescending { it.failedChapters }
            .thenBy { it.name.lowercase() },
    )
}
