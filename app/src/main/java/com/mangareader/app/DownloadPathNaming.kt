package com.mangareader.app

/**
 * Cross-platform folder naming policy for readable download paths.
 */
internal object DownloadPathNaming {
    private const val MAX_NAME = 60

    private val ILLEGAL = Regex("""[\\/:*?"<>|\x00-\x1F]""")
    private val WHITESPACE = Regex("""\s+""")
    private val RESERVED = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    )

    fun clean(raw: String, fallback: String): String {
        var name = raw.replace(ILLEGAL, " ")
        name = name.replace(WHITESPACE, " ").trim()
        name = name.trimEnd('.', ' ')

        if (name.length > MAX_NAME) {
            name = name.take(MAX_NAME).trimEnd('.', ' ')
        }

        if (name.uppercase() in RESERVED) {
            name = "_$name"
        }

        return name.ifBlank { fallback }
    }

    /**
     * Keeps a free/already-owned name, otherwise adds a stable id-derived suffix.
     */
    fun unique(
        desired: String,
        ownerId: String,
        takenBy: Map<String, String>,
    ): String {
        val holder = takenBy[desired]
        if (holder == null || holder == ownerId) return desired
        return desired + " (" + offlineKey(ownerId).take(6) + ")"
    }
}
