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
            code = 81,
            name = "0.81",
            header = "Series that loaded chapters only sometimes",
            body = "0.80 got chapters working on the newer extensions, but " +
                "opening a series asks for its details and its chapter list " +
                "at the same time, and some extensions refuse two overlapping " +
                "requests for the same series. Whichever arrived second " +
                "failed, so the same source would work on one series and not " +
                "the next with no pattern to it.\n\n" +
                "The two requests are now ordered rather than simultaneous.",
        ),
        ReleaseNote(
            code = 80,
            name = "0.80",
            header = "Sources that fetch chapters and details together",
            body = "Newer extensions can get a series' details and its chapter " +
                "list in a single request, and some of them now do that and " +
                "nothing else. This app only knew how to ask for the two " +
                "separately, so those sources browsed normally and then " +
                "failed on every series with \"Could not list chapters\".\n\n" +
                "It can now ask the new way, and still asks the old way for " +
                "extensions that expect it.\n\n" +
                "0.79 changed how every source was fetched while chasing this. " +
                "That change has been undone \u2014 it was based on a wrong " +
                "guess and only this part was needed.",
        ),
        ReleaseNote(
            code = 79,
            name = "0.79",
            header = "Sources that browsed but wouldn't open anything",
            body = "Elite Babes listed its catalogue perfectly and then failed " +
                "on every series with \"Could not list chapters\". The cause was " +
                "in this app: it was asking extensions for chapters, details, " +
                "pages and images through an old interface that modern " +
                "extensions no longer implement \u2014 they answer on the " +
                "current one, and switch the old one off.\n\n" +
                "The app now uses the current interface throughout. This may " +
                "well fix other sources that half-worked in the same way: " +
                "browsing fine, failing the moment you opened something.",
        ),
        ReleaseNote(
            code = 78,
            name = "0.78",
            header = "Source errors say what actually went wrong",
            body = "When a source failed in a way that carried no message, the " +
                "app showed a generic line like \"Could not list chapters\" \u2014 " +
                "which reads the same as a series that genuinely has none. " +
                "Errors now name the underlying failure, so a real problem is " +
                "distinguishable from an empty result.",
        ),
        ReleaseNote(
            code = 77,
            name = "0.77",
            header = "An extension can no longer close the app, for real this time",
            body = "Two previous releases tried to stop a broken extension " +
                "taking the app down with it, and both missed a path. This one " +
                "handles it where the app calls into an extension rather than " +
                "at each of the places that might be affected, so there is no " +
                "list of paths left to miss.\n\n" +
                "Also adds another piece of the HTTP library that current " +
                "extensions expect.",
        ),
        ReleaseNote(
            code = 76,
            name = "0.76",
            header = "The network library is current again",
            body = "Elite Babes needed a piece of the HTTP library newer than " +
                "the one this app shipped, and asking for it took the whole " +
                "app down. The library is now up to date, so the source should " +
                "work.\n\n" +
                "The crash could also happen with no Elite Babes screen open " +
                "at all: a queued download reaching the same source did it on " +
                "startup. Downloads and the library refresh now survive a " +
                "failure like that instead of ending the app, which was the " +
                "half of 0.75 that didn't go far enough.",
        ),
        ReleaseNote(
            code = 75,
            name = "0.75",
            header = "A broken extension can no longer close the app",
            body = "An extension built against a newer version of the source " +
                "API than this app provides could take the whole app down \u2014 " +
                "no message, no error, just gone. Opening Elite Babes did " +
                "exactly that.\n\n" +
                "Source failures of that kind are now caught and shown like any " +
                "other error, and the message names the missing piece rather " +
                "than saying something generic. That does not make such an " +
                "extension work; it means you can see why it doesn't, and the " +
                "rest of the app keeps running.",
        ),
        ReleaseNote(
            code = 74,
            name = "0.74",
            header = "Elite Babes works again, and the Downloads tab stops freezing",
            body = "0.73 fixed one half of why some updated extensions refused " +
                "to load anything. This is the other half \u2014 newer " +
                "extensions check the app's network setup by name, and one " +
                "piece of it was written in a way they couldn't recognise " +
                "even though it did the right job.\n\n" +
                "Opening the Downloads tab could also hang the app. It was " +
                "measuring every downloaded chapter, and scanning the whole " +
                "library for old downloads it might have lost track of, " +
                "before it drew anything. That scan is a one-time repair and " +
                "now runs once instead of every time, and the rest happens in " +
                "the background while the screen stays usable.",
        ),
        ReleaseNote(
            code = 73,
            name = "0.73",
            header = "Sources that stopped working after an extension update",
            body = "Updating an extension could leave its source unable to " +
                "load anything, with an error about a missing interceptor. " +
                "The extension was right and the app was at fault: a piece of " +
                "the network stack that newer extensions expect had been " +
                "present in the code but never actually switched on.\n\n" +
                "It is switched on now. If a source stopped working after you " +
                "updated its extension, it should work again.",
        ),
        ReleaseNote(
            code = 72,
            name = "0.72",
            header = "The app opens in about three seconds",
            body = "It was taking around thirty. The cause turned out to be " +
                "the download badge on library covers: to decide which " +
                "covers get one, the library was asking the same question " +
                "the Downloads tab asks \u2014 which measures every " +
                "downloaded chapter and scans the whole library for " +
                "downloads it might have lost track of. All of it before the " +
                "first frame could be drawn, every single time.\n\n" +
                "The library now asks a smaller question, and the badges are " +
                "unchanged.\n\n" +
                "The startup screen also shows the app icon now, instead of " +
                "an empty rectangle.\n\n" +
                "Two smaller things: the refresh summary survives closing the " +
                "app, so its numbers aren't lost the moment you install an " +
                "update, and it now lists which sources the failures came " +
                "from rather than only how many there were.",
        ),
        ReleaseNote(
            code = 71,
            name = "0.71",
            header = "Finding out why the app is slow to open",
            body = "No change to how the app behaves. Opening it has been slow " +
                "for a while, and until now the only evidence was that it felt " +
                "slow \u2014 which is not enough to fix the right thing.\n\n" +
                "Settings \u203a Advanced now has a Startup timings report, " +
                "next to the extension diagnostics. It shows how long the app " +
                "spent reading its saved data before it could draw anything, " +
                "and how large that saved data has grown. The next release can " +
                "then fix whichever part is actually the problem instead of " +
                "the part that looked likeliest.",
        ),
        ReleaseNote(
            code = 70,
            name = "0.70",
            header = "Covers on protected sources, and a quieter Extensions tab",
            body = "Some sources listed every title correctly and then showed a " +
                "grid of grey boxes where the covers should be. Page images " +
                "already went out carrying the details the source needs to " +
                "recognise its own request; cover images did not, so sites " +
                "that check turned them away. Covers now go out the same way " +
                "pages do.\n\n" +
                "The Extensions tab re-downloaded the full extension list " +
                "every time it was opened, which on a large repository is a " +
                "few megabytes and a visible wait. The list is now kept for " +
                "ten minutes and reused, while still checking what is " +
                "installed each time you look. A repository that can't be " +
                "reached falls back to the last list it gave instead of " +
                "emptying the screen.",
        ),
        ReleaseNote(
            code = 69,
            name = "0.69",
            header = "Chapter counts hold up under a refresh",
            body = "The library refresh writes chapter counts a hundred series " +
                "at a time, and it does that from several places at once. Two " +
                "of those writes landing together could overwrite each other, " +
                "losing a batch of counts - and, since 0.68, losing the marks " +
                "that say which series a refresh has already reached, so a " +
                "resume would fetch them again. Opening a series while a " +
                "refresh was running could do the same thing. Writes now take " +
                "turns.\n\n" +
                "The summary a finished refresh leaves behind can be dismissed, " +
                "so the row goes back to offering a plain refresh instead of " +
                "showing the last one's totals until the app is closed.",
        ),
        ReleaseNote(
            code = 68,
            name = "0.68",
            header = "A stopped refresh now resumes",
            body = "0.67 said stopping a library refresh was safe and that " +
                "running it again would pick up the rest. The first half was " +
                "true and the second was not - it started again from the top " +
                "and re-fetched everything it had already counted. On a large " +
                "library that meant a refresh could only ever get as far as " +
                "the longest run you left it alone for.\n\n" +
                "Every series a refresh counts is now marked as belonging to " +
                "that run, so starting it again fetches only what it never " +
                "reached. Settings - Library shows Resume refresh with how far " +
                "it got, and a Start over next to it for when the counts are " +
                "old rather than incomplete. Series that failed or whose " +
                "extension is missing are retried on a resume rather than " +
                "treated as done.\n\n" +
                "Stopping the refresh when it wasn't running left an " +
                "unremovable notification behind. Fixed."
        ),
        ReleaseNote(
            code = 67,
            name = "0.67",
            header = "Refresh the whole library at once",
            body = "Settings - Library - Refresh library fetches a chapter list " +
                "for every saved series, so unread counts, the badge and the " +
                "Unread, Started and Completed filters cover your whole " +
                "library instead of only the series you have opened.\n\n" +
                "It runs in the background with a progress notification and " +
                "keeps going with the app closed. Stopping it is safe - " +
                "whatever it has already counted is kept, and running it again " +
                "picks up the rest.\n\n" +
                "It is one request per series and it paces itself per source, " +
                "so a few thousand series takes a while. Best left running on " +
                "Wi-Fi. Nothing is downloaded, only chapter lists - though the " +
                "offline chapter lists get refreshed along the way, so series " +
                "you have saved open more accurately without a connection."
        ),
        ReleaseNote(
            code = 65,
            name = "0.65",
            header = "Unread counts, and three smaller fixes",
            body = "The library can count chapters now. Covers carry an unread " +
                "badge, and the options sheet gains Unread, Started and " +
                "Completed filters plus sorting by unread count, chapter count " +
                "or latest chapter.\n\n" +
                "It only knows about series you have opened at least once - " +
                "counting the rest would mean reading a file per series every " +
                "time the grid draws. Anything not yet counted stays out of " +
                "those views instead of being guessed at, and joins them the " +
                "first time you open it. All of it can be switched off under " +
                "Display and Filter.\n\n" +
                "Also: the page number is outlined now, so it stays readable " +
                "over a dark panel or a blown-out white one rather than only " +
                "over the background colour. Backing out of a chapter returns " +
                "to where you were in the chapter list instead of the top. And " +
                "tapping a cover on a series page opens it full screen, with " +
                "pinch to zoom."
        ),
        ReleaseNote(
            code = 64,
            name = "0.64",
            header = "Vertical slider fixes, and readable page numbers",
            body = "The vertical slider ran backwards - dragging down walked " +
                "towards the start of the chapter. It was rotated the wrong way, " +
                "which looks identical sitting still. It is also half the screen " +
                "tall now rather than a fixed stub.\n\n" +
                "Page numbers were drawn white whatever the reader background " +
                "was, so on a white page they were invisible. The colour now " +
                "follows the background, which also covers the Theme option and " +
                "the message shown when a page fails to load."
        ),
        ReleaseNote(
            code = 63,
            name = "0.63",
            header = "The page slider can stand up",
            body = "Reader settings, under Layout, now has a Page slider choice: " +
                "horizontal keeps it in the control bar as before, vertical " +
                "stands it up against the right edge.\n\n" +
                "Vertical suits long strip, where the thumb then travels the same " +
                "direction the pages do. Both behave the same otherwise - the " +
                "jump happens when you let go, and the page count follows the " +
                "thumb while you drag."
        ),
        ReleaseNote(
            code = 62,
            name = "0.62",
            header = "Zoom and a page slider in the reader",
            body = "Paged modes zoom. Pinch, double-tap, and drag to move around " +
                "a zoomed page; swiping to the next page still works, because " +
                "the swipe is only taken once a zoomed page has nothing left to " +
                "pan. Grayscale and invert still apply.\n\n" +
                "Long strip is deliberately left alone. A pinch there competes " +
                "with the scroll the mode is built on, and doing it properly is " +
                "its own piece of work rather than a switch to flip.\n\n" +
                "The controls now carry a slider across the whole chapter. It " +
                "jumps when you let go rather than while dragging, and the page " +
                "count above it follows the thumb so you can see where you are " +
                "about to land."
        ),
        ReleaseNote(
            code = 61,
            name = "0.61",
            header = "Uninstall works, and tag searches come back",
            body = "0.59's uninstall button asked the system to remove the " +
                "package without holding the permission that lets an app ask, " +
                "so the request was refused before anything appeared - a tap, a " +
                "flicker, and the extension still installed.\n\n" +
                "The permission is declared now, the system's confirmation opens " +
                "inside the app rather than in a window of its own, and if the " +
                "package is still there afterwards you get told so instead of " +
                "being left to guess.\n\n" +
                "Backing out of a tag search now returns to the series the tag " +
                "was on, rather than dropping you on the Library tab. Opening " +
                "something from the results still behaves normally - back goes " +
                "to the results."
        ),
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
