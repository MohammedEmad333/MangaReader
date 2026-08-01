package com.mangareader.app

/**
 * Works a chapter number out of a chapter's *name*.
 *
 * **This exists because `SChapter.chapter_number` is nearly always empty.** It
 * is declared in the vendored API and defaults to `-1f`, and `prepareNewChapter`
 * is an empty hook — upstream Tachiyomi fills the number in the *app*, from the
 * name, and extensions were written against that. 0.107 wired `Chapter.number`
 * straight from `chapter_number` on the strength of the field existing, which
 * shipped a sort with nothing to sort on and a display mode identical to the one
 * beside it.
 *
 * The general form, and it is the reusable part: **a vendored field being
 * present says nothing about anything filling it.** The check that would have
 * caught this is one grep of the extension source for `chapter_number =`, not a
 * grep of the API for `chapter_number`.
 *
 * Ported from TachiyomiSY's `tachiyomi.domain.chapter.service.ChapterRecognition`,
 * kept behaviourally identical because its regexes encode years of real chapter
 * titles. Returns `Float` rather than SY's `Double` to match [Chapter.number],
 * and folds SY's `-2.0` "known to be unnumbered" case into [Chapter.NO_NUMBER] —
 * this app has no writer that would ever set it.
 */
internal object ChapterRecognition {

    private const val NUMBER_PATTERN = """([0-9]+)(\.[0-9]+)?(\.?[a-z]+)?"""

    /** `Mokushiroku Alice Vol.1 Ch. 4: Misrepresentation` -> 4 */
    private val basic = Regex("""(?<=ch\.) *$NUMBER_PATTERN""")

    /** `Bleach 567: Down With Snowwhite` -> 567 */
    private val number = Regex(NUMBER_PATTERN)

    /** `Prison School 12 v.1 vol004 version1243 volume64` -> `Prison School 12` */
    private val unwanted = Regex("""\b(?:v|ver|vol|version|volume|season|s)[^a-z]?[0-9]+""")

    /** `One Piece 12 special` -> `One Piece 12special` */
    private val unwantedWhiteSpace = Regex("""\s(?=extra|special|omake)""")

    /**
     * @param seriesTitle stripped out of the name first, so `One Piece 1052`
     *   doesn't read as chapter 1 from a series whose title contains a number.
     *   Pass a blank string when it isn't known — the parse still works, it just
     *   loses that one guard.
     * @param fromSource the source's own `chapter_number`, honoured when it is
     *   actually set. A source that does the work is more likely to be right
     *   than a regex over its own title.
     */
    fun parse(
        seriesTitle: String,
        chapterName: String,
        fromSource: Float = Chapter.NO_NUMBER
    ): Float {
        if (fromSource > Chapter.NO_NUMBER) return fromSource

        val clean = chapterName.lowercase()
            .replace(seriesTitle.lowercase(), "")
            .trim()
            // Commas and hyphens become decimal points: "Ch 5-2" is 5.2.
            .replace(',', '.')
            .replace('-', '.')
            .replace(unwantedWhiteSpace, "")

        val matches = number.findAll(clean)
        when {
            matches.none() -> return Chapter.NO_NUMBER
            matches.count() > 1 -> {
                // More than one number in the name, so volume and version tags
                // are stripped before picking — otherwise "Vol.4 Ch.12" is 4.
                unwanted.replace(clean, "").let { name ->
                    basic.find(name)?.let { return numberFrom(it) }
                    // Searched again rather than reusing the earlier match: the
                    // strip above may have removed the one that was found.
                    number.find(name)?.let { return numberFrom(it) }
                }
            }
        }
        return numberFrom(matches.first())
    }

    private fun numberFrom(match: MatchResult): Float {
        val initial = match.groups[1]?.value?.toFloatOrNull() ?: return Chapter.NO_NUMBER
        return initial + decimalFrom(match.groups[2]?.value, match.groups[3]?.value)
    }

    /**
     * The fractional part, from either a real decimal or a word.
     *
     * The word cases are SY's and are worth keeping: an "extra" sorts just after
     * the chapter it follows rather than into a gap of its own.
     */
    private fun decimalFrom(decimal: String?, alpha: String?): Float {
        if (!decimal.isNullOrEmpty()) return decimal.toFloatOrNull() ?: 0f
        if (!alpha.isNullOrEmpty()) {
            if (alpha.contains("extra")) return 0.99f
            if (alpha.contains("omake")) return 0.98f
            if (alpha.contains("special")) return 0.97f
            val trimmed = alpha.trimStart('.')
            if (trimmed.length == 1) return alphaPostfix(trimmed[0])
        }
        return 0f
    }

    /** `x.a` -> `x.1`, `x.b` -> `x.2`, and nothing past `x.i`. */
    private fun alphaPostfix(alpha: Char): Float {
        val n = alpha.code - ('a'.code - 1)
        if (n >= 10) return 0f
        return n / 10f
    }
}
