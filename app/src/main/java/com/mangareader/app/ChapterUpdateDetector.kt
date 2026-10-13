package com.mangareader.app

/**
 * Compare chapter snapshots independently of automatic-download preferences.
 * A missing previous snapshot means initial indexing, not new releases.
 * IDs are preferred, with normalized names as a fallback for URL migrations.
 */
internal object ChapterUpdateDetector {
    fun newlyAvailable(previous: List<Chapter>, current: List<Chapter>): List<Chapter> {
        if (previous.isEmpty() || current.isEmpty()) return emptyList()
        val oldIds = previous.mapTo(HashSet()) { it.id }
        val oldNames = previous.mapTo(HashSet()) { normalize(it.name) }
        val seenIds = HashSet<String>()
        val seenNames = HashSet<String>()
        return current.filter { chapter ->
            val name = normalize(chapter.name)
            val unique = seenIds.add(chapter.id) && seenNames.add(name)
            unique && chapter.id !in oldIds && name !in oldNames
        }
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(java.util.Locale.ROOT)
            .replace(Regex("[\\s\\p{Zs}]+"), " ")
}
