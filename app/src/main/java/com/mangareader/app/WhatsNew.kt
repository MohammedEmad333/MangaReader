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
