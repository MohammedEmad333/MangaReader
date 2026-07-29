package com.mangareader.app

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One release's note.
 *
 * [code] is the `versionCode`, not the name — it's what the comparison is done
 * on, because "0.9" and "0.10" don't order as strings and the name is only ever
 * "0.<code>" anyway.
 */
data class ReleaseNote(
    val code: Int,
    val name: String,
    val header: String,
    val body: String
)

/**
 * The changelog, newest first.
 *
 * **Add an entry here in the same commit that bumps `versionCode`.** A release
 * with no entry isn't an error — it's simply skipped in the list — but it's also
 * invisible to the user, which defeats the point.
 *
 * Kept as source rather than an asset or a network fetch on purpose: it ships
 * with the APK it describes, so there is no version where the app and its notes
 * can disagree, and no failure mode where the dialog is empty because the phone
 * was offline.
 */
object Changelog {
    val notes: List<ReleaseNote> = listOf(
        ReleaseNote(
            code = 60,
            name = "0.60",
            header = "Tappable tags, and a swipeable Browse",
            body = "Tapping a tag on a series now offers to search it: in that " +
                "series' own source, across every source at once, or to copy it. " +
                "Either search leaves the series behind and shows the results, " +
                "so back returns to whichever list you were looking at.\n\n" +
                "Sources and Extensions swipe, the way the library's category " +
                "tabs do."
        ),
        ReleaseNote(
            code = 59,
            name = "0.59",
            header = "Uninstall extensions, and a consistent reader back button",
            body = "Installed extensions now have an uninstall button in the " +
                "Extensions list, next to the update button where there is one. " +
                "It opens the system's uninstall prompt, so nothing is removed " +
                "without your say-so, and the list re-reads itself when you come " +
                "back.\n\n" +
                "The reader's back arrow is the same button every other screen " +
                "uses. It was the last one drawing its own."
        ),
        ReleaseNote(
            code = 58,
            name = "0.58",
            header = "Fixes chapters marking themselves read",
            body = "0.57 marked a chapter read the moment it was opened in long " +
                "strip, whatever page you left from, and saved the last page as " +
                "your position.\n\n" +
                "The end-of-chapter check asked the page list whether it could " +
                "still scroll. A list answers no to that until it has been " +
                "measured for the first time, and the check ran before that " +
                "happened - so every chapter looked finished on arrival. It now " +
                "asks whether the bottom of the last page is actually on screen, " +
                "which nothing can answer until there is a layout to read.\n\n" +
                "Chapters wrongly marked read in 0.57 stay that way: long-press " +
                "them in the chapter list and choose Mark unread. Any that reopen " +
                "at the last page will correct themselves once read again."
        ),
        ReleaseNote(
            code = 57,
            name = "0.57",
            header = "Reader, history covers, and the extension list",
            body = "Long strip could not reach its last page. It reported whichever " +
                "page was at the top of the screen, and the final page is visible " +
                "at the bottom long before it gets to the top - so the counter " +
                "stopped short and chapters were never marked read. It now " +
                "reports the end when the strip can't scroll further.\n\n" +
                "Long strip also shifted around on its own while you sat still. " +
                "A page that hasn't decoded yet measures zero tall, so the whole " +
                "chapter collapsed and then shoved itself apart as the images " +
                "arrived. Pages now reserve space before they load.\n\n" +
                "History covers show again. A cover from an extension is a web " +
                "address and was being opened as if it were a file on disk.\n\n" +
                "Extension lists in the newer repository format are read. If your " +
                "repository shows only \u201cOutdated App\u201d and \u201cUpdate to " +
                "Mihon\u201d, its address needs changing too - see More \u2192 " +
                "Extension repos."
        ),
        ReleaseNote(
            code = 56,
            name = "0.56",
            header = "Source screens list, they don't filter",
            body = "The category chips are gone from per-source browse. They only " +
                "ever filtered the page already on screen - the first twenty or " +
                "so titles of Popular - against your library categories, so they " +
                "were almost always empty, and they switched off \u201cLoad more\u201d " +
                "while active. Popular, Latest and Filter are the listing " +
                "controls; filtering your library is the library's job.\n\n" +
                "This also removes the Default chip that 0.55 had just fixed. It " +
                "was correct and still not worth having."
        ),
        ReleaseNote(
            code = 55,
            name = "0.55",
            header = "Four library and browse fixes",
            body = "Random sort works. It was reordering the grid all along - but " +
                "the grid identifies entries by series, so after a shuffle it " +
                "chased whatever had been at the top down to its new place and " +
                "scrolled there, which looked like nothing had happened except a " +
                "lurch downwards. A reorder now starts at the top, and the " +
                "shuffle itself mixes properly rather than leaving series from " +
                "one source clumped together.\n\n" +
                "The library and per-source grids keep their place when you open " +
                "an entry and come back, and the library keeps its search. Both " +
                "were stored inside the screen, which opening a series replaces " +
                "outright - the same thing that used to lose the category tab.\n\n" +
                "The Default chip on a source screen now lists what the library's " +
                "Default tab does. Default isn't a category series are filed " +
                "under, it's where one sits when it's filed under nothing, and " +
                "that screen was matching only the ones filed there by hand."
        ),
        ReleaseNote(
            code = 54,
            name = "0.54",
            header = "Library filter, sort, display and group options",
            body = "The library bar's second icon now opens a sheet with four " +
                "tabs.\n\n" +
                "Filter: downloaded, local source, and read - each tri-state, so " +
                "tap once to require, twice to exclude, three times to clear. " +
                "Sort: alphabetically, date added, last read, or random, and " +
                "tapping the active one reverses it. Display: compact, " +
                "comfortable, cover-only or list, a fixed or automatic column " +
                "count, download and local badges, and whether the tabs show at " +
                "all or carry a count. Group: by category, or not at all.\n\n" +
                "Filters that need a chapter list per series - unread, started, " +
                "completed, chapter counts - aren't here. The app only keeps a " +
                "chapter list for a series once it has been opened, so answering " +
                "those across the whole library would mean thousands of reads " +
                "every time the grid draws."
        ),
        ReleaseNote(
            code = 53,
            name = "0.53",
            header = "Change categories for a whole selection",
            body = "Select any number of entries and the pencil in the selection " +
                "bar now edits all of their categories at once.\n\n" +
                "The boxes are three-state. A category every selected entry is " +
                "already in starts checked, one none of them is in starts empty, " +
                "and one that only some of them are in starts filled - meaning " +
                "leave it alone. Tapping cycles on, off, and back to where it " +
                "started, so a mixed category can be forced either way or " +
                "restored without touching the entries that were already right."
        ),
        ReleaseNote(
            code = 52,
            name = "0.52",
            header = "Library tabs, multi-select, and What's new",
            body = "Categories are real tabs now and you can swipe between them. " +
                "The \"All\" tab is gone. Opening a series and coming back returns " +
                "to the category you were in.\n\n" +
                "Long-press any cover to start selecting; the bar at the top can " +
                "select every entry in the tab, change one entry's categories, or " +
                "remove everything selected in a single write.\n\n" +
                "The library bar gained search and an options menu (cover size). " +
                "Entries filed under Read are dimmed. And this dialog exists — it " +
                "appears once after an update, listing everything since the version " +
                "that was installed before."
        )
        // Older releases predate this dialog and have no notes on purpose:
        // back-filling 51 of them would mean writing them from the commit log,
        // and a wrong changelog is worse than a short one.
    )

    /**
     * Notes for everything newer than [installed], up to and including [current].
     *
     * Half-open at the bottom: the version already on the phone has been seen.
     */
    fun since(installed: Int, current: Int): List<ReleaseNote> =
        notes.filter { it.code in (installed + 1)..current }
            .sortedByDescending { it.code }
}

object WhatsNew {
    private const val KEY_SEEN = "whats_new_seen_code"

    private fun prefs(c: Context) =
        c.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    /**
     * What to show on this launch, or empty for nothing.
     *
     * Three cases, and the middle one is the only interesting one:
     *
     * 1. **The marker is present.** Normal path. Show everything between it and
     *    the running build.
     * 2. **No marker, but there is a library.** This build is the first one that
     *    ever wrote a marker, so an existing user is upgrading into the feature
     *    and there is no honest "from" version to diff against. Show the running
     *    build's note only — which is also exactly what this update is.
     * 3. **No marker and no library.** A fresh install. Record and show nothing:
     *    a changelog is the wrong first screen for someone with nothing to catch
     *    up on.
     *
     * `library_json` is the probe for (2) vs (3) because it is the oldest key
     * the app has written and the one a real user is guaranteed to have.
     */
    fun pending(context: Context): List<ReleaseNote> {
        val p = prefs(context)
        val current = BuildConfig.VERSION_CODE

        if (!p.contains(KEY_SEEN)) {
            markSeen(context)
            val existingInstall = p.contains("library_json")
            return if (existingInstall) Changelog.since(current - 1, current) else emptyList()
        }

        val seen = p.getInt(KEY_SEEN, current)
        if (seen >= current) return emptyList()
        return Changelog.since(seen, current)
    }

    /**
     * Records the current version as seen.
     *
     * Called when the dialog is dismissed, and also when [pending] finds an
     * update with no notes to show — otherwise a release with no changelog
     * entry would leave the marker behind and the *next* update would replay
     * both.
     */
    fun markSeen(context: Context) {
        prefs(context).edit().putInt(KEY_SEEN, BuildConfig.VERSION_CODE).apply()
    }
}

/**
 * The post-update dialog.
 *
 * The newest release is open; everything between the installed version and this
 * one is collapsed to a header with a chevron. That ordering is the whole
 * design — the reason the dialog opened is the version at the top, and the ones
 * underneath are context for someone who skipped a few builds.
 */
@Composable
internal fun WhatsNewDialog(notes: List<ReleaseNote>, onDismiss: () -> Unit) {
    if (notes.isEmpty()) return

    // Keyed by version code so it survives recomposition; only the newest
    // starts open.
    val expanded = remember(notes) { mutableStateMapOf(notes.first().code to true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's new") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                notes.forEachIndexed { index, note ->
                    val open = expanded[note.code] == true
                    if (index > 0) {
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded[note.code] = !open }
                            .padding(vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Version ${note.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                note.header,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Icon(
                            if (open) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (open) "Collapse" else "Expand"
                        )
                    }
                    // A plain `if`, not AnimatedVisibility: the animation
                    // artifact is only on this classpath transitively, and a
                    // crossfade is not worth finding that out at build time.
                    if (open) {
                        Text(
                            note.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Got it") } }
    )
}
