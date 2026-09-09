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
            code = 203,
            name = "0.203",
            header = "An empty source now tells you why",
            body = "When a source's listing comes back empty, the screen used " +
                "to say only “Nothing found.” Now, when the site " +
                "loaded fine but no titles could be read from it — almost " +
                "always because the site changed and the extension is behind — " +
                "it says so and offers the connection probe, so you can tell a " +
                "source that needs updating from one that's genuinely empty.",
        ),
        ReleaseNote(
            code = 202,
            name = "0.202",
            header = "The filter sheet is snappy again",
            body = "Ticking a genre or any other option in a source's filter " +
                "used to lag, because every tap quietly rebuilt the whole sheet. " +
                "Now a tap updates only the row you touched.",
        ),
        ReleaseNote(
            code = 201,
            name = "0.201",
            header = "History by series, and tags search by genre",
            body = "History now shows one entry per series — the latest " +
                "chapter and the page you were on — instead of a separate " +
                "row for every chapter. And tapping a tag on a series now " +
                "searches that source by genre where it can, so you get " +
                "everything tagged with it rather than everything with the word " +
                "in its title.",
        ),
        ReleaseNote(
            code = 200,
            name = "0.200",
            header = "Downloaded chapters show their size",
            body = "A downloaded chapter now shows how much space it takes, next " +
                "to its date on the series screen.",
        ),
        ReleaseNote(
            code = 199,
            name = "0.199",
            header = "Light themes get colour too, plus Tidal Wave",
            body = "Light mode now carries each theme's colour instead of plain " +
                "white \u2014 backgrounds, nav bar and cards all take a soft tint. " +
                "Added a new Tidal Wave (ocean blue) theme.",
        ),
        ReleaseNote(
            code = 198,
            name = "0.198",
            header = "Stronger theme colours",
            body = "Themed backgrounds now carry more of the theme's colour, and " +
                "cards and the nav bar separate from them more clearly.",
        ),
        ReleaseNote(
            code = 197,
            name = "0.197",
            header = "Themes now colour the whole app",
            body = "Colour themes now tint the background, nav bar and cards, not " +
                "just the accent \u2014 so a theme reads across the whole app. The " +
                "bottom bar uses real icons that pick up the theme colour, and the " +
                "More menu has coloured icons too.",
        ),
        ReleaseNote(
            code = 196,
            name = "0.196",
            header = "Theme picker redesign",
            body = "The theme setting now shows a live preview of each theme and " +
                "a System / Light / Dark toggle, matching the look you'd expect. " +
                "Same themes and pure-black option as before, easier to see.",
        ),
        ReleaseNote(
            code = 195,
            name = "0.195",
            header = "Full colour themes, and a pure-black mode",
            body = "Settings \u2192 Appearance now offers complete colour themes "  +
                "\u2014 Midnight Dusk, Green Apple, Strawberry, Tako and more \u2014 "  +
                "each setting the whole palette, not just the accent. There's also "  +
                "a pure-black (AMOLED) toggle for true-black backgrounds in dark "  +
                "mode. Your old accent carries over automatically.",
        ),
        ReleaseNote(
            code = 194,
            name = "0.194",
            header = "Refresh now clears deleted-download badges",
            body = "If you deleted a series' files outside the app, its "  +
                "downloaded badge could linger. Pulling to refresh the Library, "  +
                "or refreshing a series, now re-checks what's actually on disk "  +
                "and drops the badge.",
        ),
        ReleaseNote(
            code = 193,
            name = "0.193",
            header = "Pull down to refresh the Library",
            body = "The Library tab now refreshes on a pull-down, the same as "  +
                "History and Downloads. It re-reads what's on the device — so "  +
                "a series whose files you deleted elsewhere stops showing as "  +
                "downloaded — without kicking off a full chapter fetch.",
        ),
        ReleaseNote(
            code = 192,
            name = "0.192",
            header = "Animated covers now animate",
            body = "Series with a GIF or animated cover used to show it "  +
                "frozen on the first frame. It now plays, the same as it "  +
                "would in a browser.",
        ),
        ReleaseNote(
            code = 189,
            name = "0.189",
            header = "Find videos goes straight to the video",
            body = "No more blank player screen. The page loads out of "  +
                "sight, finds the video address by itself, and offers it to "  +
                "your video player.\n\nIt also scans the chapter at the top "  +
                "of the list you are looking at, rather than whichever one "  +
                "happened to be first internally.",
        ),
        ReleaseNote(
            code = 188,
            name = "0.188",
            header = "The video link button actually opens now",
            body = "It worked in 0.187 and had nowhere to show its result, "  +
                "so tapping it looked like nothing at all.",
        ),
        ReleaseNote(
            code = 187,
            name = "0.187",
            header = "Get the video link and open it anywhere",
            body = "Instead of fighting to draw the video inside the app, "  +
                "the player screen can now hand you the actual video address "  +
                "\u2014 open it in MX Player, VLC, or anything else you "  +
                "have.\n\nUse the download icon in the bar while the video "  +
                "is playing.",
        ),
        ReleaseNote(
            code = 186,
            name = "0.186",
            header = "One more idea about the missing picture",
            body = "The video may be playing behind the page rather than in "  +
                "it, with the page\u2019s own white painted over the top. "  +
                "This build makes the page transparent to find out.\n\nIf "  +
                "the screen turns black instead of showing the video, that "  +
                "idea is wrong too and this is the end of the road.",
        ),
        ReleaseNote(
            code = 185,
            name = "0.185",
            header = "Still looking for the picture",
            body = "The last attempt did not help and has been taken back "  +
                "out. This one checks two things nobody had looked at: "  +
                "whether the page has more than one video, and whether "  +
                "something above it in the page is hiding it.",
        ),
        ReleaseNote(
            code = 184,
            name = "0.184",
            header = "The player screen stops jumping to adverts",
            body = "Tapping the video threw you out to a shopping site \u2014 "  +
                "these embed hosts make their money from exactly that. The "  +
                "screen now stays on the player and refuses to be "  +
                "navigated away.\n\nIt also carries the hardware-surface fix "  +
                "from 0.183, which never reached you.",
        ),
        ReleaseNote(
            code = 183,
            name = "0.183",
            header = "The picture should arrive now",
            body = "Everything about the video checked out \u2014 full "  +
                "screen, visible, decoding every frame \u2014 so the missing "  +
                "piece was the app not giving it a hardware surface to draw "  +
                "on. It has one now.",
        ),
        ReleaseNote(
            code = 182,
            name = "0.182",
            header = "Finding where the picture went",
            body = "The video decodes every frame and drops none, so the "  +
                "picture exists. This release reports where the player puts "  +
                "it on the page and what is sitting on top of it.",
        ),
        ReleaseNote(
            code = 181,
            name = "0.181",
            header = "Counting the frames",
            body = "Sound plays but no picture. That has two opposite "  +
                "causes, so this release counts decoded frames rather than "  +
                "guessing which one it is.",
        ),
        ReleaseNote(
            code = 180,
            name = "0.180",
            header = "A play button on the player screen",
            body = "The video was loading fine and simply never started \u2014 "  +
                "the site\u2019s own play overlay does not draw here. There "  +
                "is a play button in the bar now.\n\nIf it still refuses, "  +
                "the reason appears in the line at the bottom.",
        ),
        ReleaseNote(
            code = 179,
            name = "0.179",
            header = "The player screen now says what it sees",
            body = "Two attempts at the blank player were guesses that "  +
                "fitted the symptom and were both wrong. This one reports "  +
                "what the page actually contains \u2014 whether there is a "  +
                "video at all, whether it is playing, and what size it "  +
                "thinks it is \u2014 in a line under the page.",
        ),
        ReleaseNote(
            code = 178,
            name = "0.178",
            header = "The player is visible now",
            body = "0.177 loaded the video and played it, but showed a blank "  +
                "page \u2014 the player had asked to go fullscreen and the "  +
                "app was not listening.\n\nFullscreen works properly now, "  +
                "and back leaves fullscreen before it leaves the player.",
        ),
        ReleaseNote(
            code = 177,
            name = "0.177",
            header = "Embedded players open inside the app",
            body = "Opening one in the browser got an error page, because "  +
                "the player refuses requests that do not come from the "  +
                "gallery page. It now opens in the app, which can say where "  +
                "it came from.\n\nAnalytics frames are also no longer "  +
                "offered as players.",
        ),
        ReleaseNote(
            code = 176,
            name = "0.176",
            header = "Find videos can open an embedded player",
            body = "Some pages build their player with a script, so there is "  +
                "no video file to find. Where that happens the scan now "  +
                "offers the embedded player itself, which opens in your "  +
                "browser.",
        ),
        ReleaseNote(
            code = 174,
            name = "0.174",
            header = "Find videos now says what it found instead",
            body = "When the scan comes back empty it reports what was "  +
                "actually on the page \u2014 how many video, iframe and "  +
                "source tags, whether any mp4 link appears at all, and the "  +
                "markup around the first match.\n\nThat is the difference "  +
                "between a page with no videos and a page whose videos are "  +
                "built by scripts, which nothing here could ever reach.",
        ),
        ReleaseNote(
            code = 173,
            name = "0.173",
            header = "Find videos on a gallery page",
            body = "Some gallery sources put videos alongside the photos but "  +
                "never hand them to the app. The series menu now has \"Find "  +
                "videos\", which looks at the first chapter\u2019s page and "  +
                "opens anything it finds in your video player.\n\nIt is a "  +
                "best effort: if a source hides its videos behind a script, "  +
                "there will be nothing to find.",
        ),
        ReleaseNote(
            code = 172,
            name = "0.172",
            header = "No more leftover purple text",
            body = "Buttons like Got it, Remove and Close kept the old "  +
                "purple lettering whatever accent you picked. Every filled "  +
                "button now takes black or white, whichever suits your "  +
                "accent.\n\nThe Sources tab in Browse also gets its drag "  +
                "handle \u2014 0.171 put one on the wrong list.",
        ),
        ReleaseNote(
            code = 171,
            name = "0.171",
            header = "A limit on runaway sources, and a handle on the source list",
            body = "If a source claims a chapter has thousands of pages, or "  +
                "takes minutes just to list them, the app now gives up and "  +
                "says so instead of waiting.\n\nThe list of sources in "  +
                "Browse also gets the drag handle every other long list has.",
        ),
        ReleaseNote(
            code = 170,
            name = "0.170",
            header = "Back twice to leave, and no more premature verdicts",
            body = "Pressing back where it would close the app now asks for "  +
                "a second press first.\n\nAnd a series screen no longer says "  +
                "\"This source returned no chapters\" while it is still "  +
                "asking. It waits until there is an answer.",
        ),
        ReleaseNote(
            code = 168,
            name = "0.168",
            header = "Tap the download notification to see the queue",
            body = "It used to just open the app wherever you left it. It "  +
                "now goes straight to the download queue, whether the app "  +
                "was running or not.\n\nThe drag handle is also a bit "  +
                "wider, which mostly means easier to grab.",
        ),
        ReleaseNote(
            code = 167,
            name = "0.167",
            header = "The handle reaches the last chapter too",
            body = "The chapter list was the last one still stopping short. "  +
                "It was counting its own rows by hand, and the number had "  +
                "been left behind when the tag row was added.\n\nEvery list "  +
                "now asks itself how many rows it has instead of being told, "  +
                "so the count cannot fall out of date again.",
        ),
        ReleaseNote(
            code = 166,
            name = "0.166",
            header = "The drag handle reaches the end of every list",
            body = "It always stopped a little short \u2014 about a fifth of "  +
                "the last row on a list, a couple of chapters on a long "  +
                "series, the Load more button on a grid. It was counting a "  +
                "row half cut off by the edge of the screen as one that "  +
                "fitted.\n\nThe handle also keeps clear of the status and "  +
                "navigation bars now, which it did not on the series screen.",
        ),
        ReleaseNote(
            code = 165,
            name = "0.165",
            header = "The drag handle keeps clear of the screen edges",
            body = "It used to run right to the top and bottom of the "  +
                "screen. That looked wrong, and the bottom of it sat in the "  +
                "same strip as the system back and home gestures \u2014 so "  +
                "the end of a drag was competing with them.\n\nPull to "  +
                "refresh is back on Browse as well.",
        ),
        ReleaseNote(
            code = 164,
            name = "0.164",
            header = "The drag handle reaches the end of a grid",
            body = "On Browse and in the Library, dragging the handle all "  +
                "the way down stopped a row short when the last row was not "  +
                "full \u2014 the last one or two covers stayed just below "  +
                "the edge.\n\nIt measures in rows now instead of individual "  +
                "covers, so the bottom of the drag is the bottom of the grid.",
        ),
        ReleaseNote(
            code = 163,
            name = "0.163",
            header = "Refreshing Downloads really does look at the disk now",
            body = "There were two caches in the way and the last attempt "  +
                "only cleared one of them, so a series deleted outside the "  +
                "app still would not go away until you restarted it. Both "  +
                "are cleared now.\n\nPull to refresh has been taken back off "  +
                "Browse for the moment: it stopped the scroll handle reaching "  +
                "the end of the grid.",
        ),
        ReleaseNote(
            code = 162,
            name = "0.162",
            header = "Pulling to refresh Downloads actually re-reads the disk",
            body = "It was re-reading a list the app had already cached, so a "  +
                "series you deleted with a file manager stayed on the "  +
                "Downloads tab until you restarted. The pull now discards "  +
                "that cache first, which is the only way to notice a change "  +
                "the app did not make itself.\n\nBrowse gets the gesture "  +
                "too, reloading the first page of whatever you are looking "  +
                "at.",
        ),
        ReleaseNote(
            code = 161,
            name = "0.161",
            header = "The refresh arrow goes away again",
            body = "0.160 left it sitting on the screen after a pull, until "  +
                "you tapped or scrolled. It retracts by itself now.",
        ),
        ReleaseNote(
            code = 160,
            name = "0.160",
            header = "Pull down to refresh History and Downloads",
            body = "Both re-read from storage when you pull down, the same "  +
                "way a series chapter list already did. It is instant, "  +
                "because neither one goes to the network \u2014 there is no "  +
                "spinner pretending otherwise.",
        ),
        ReleaseNote(
            code = 159,
            name = "0.159",
            header = "The Start button reads properly on every accent",
            body = "0.158 gave it your accent colour but left the label and "  +
                "arrow the old purple, which was legible and obviously from "  +
                "a different palette. They are now black or white, whichever "  +
                "suits the accent behind them.\n\nAlso: History gets the "  +
                "drag handle it was missed out of in 0.157.",
        ),
        ReleaseNote(
            code = 158,
            name = "0.158",
            header = "Scroll handles everywhere, and Start follows your accent",
            body = "Per-source browse and the Extensions list are the last "  +
                "two lists to get the drag handle, so every long list in the "  +
                "app now has one.\n\nAnd the Start button on a series kept "  +
                "the default lavender whatever accent colour you picked. It "  +
                "follows the accent now.",
        ),
        ReleaseNote(
            code = 157,
            name = "0.157",
            header = "Scroll handles on two more lists",
            body = "The Downloads tab and global search now have the same "  +
                "drag handle the library and the chapter list already had, "  +
                "so a long list is one drag rather than a lot of "  +
                "flicking.\n\nAlso: a series with tags but no description "  +
                "can now expand its tags, which it could not before \u2014 "  +
                "the arrow only appeared when there was a description to "  +
                "expand.",
        ),
        ReleaseNote(
            code = 156,
            name = "0.156",
            header = "Giving up on a dead server takes a minute, not five",
            body = "0.155 stopped a chapter whose images were all on one "  +
                "unreachable server, but took about four and a half minutes "  +
                "to do it \u2014 each page was retried three times, and "  +
                "every retry waited the full timeout. Retrying a server that "  +
                "just failed to answer buys nothing, so it no longer "  +
                "does, and the error counts only the pages actually "  +
                "tried.\n\nAnd "  +
                "expanding a series description now shows every tag, "  +
                "wrapped, instead of hiding them off the right edge.",
        ),
        ReleaseNote(
            code = 155,
            name = "0.155",
            header = "A chapter stops asking a host that is not answering",
            body = "A chapter whose images all live on one unreachable "  +
                "server used to try every page in turn, waiting the full "  +
                "timeout each time \u2014 eighteen minutes of nothing for a "  +
                "thirty-page chapter, with no message until the end.\n\nIt "  +
                "now gives up after a few failures in a row and tells you "  +
                "which server would not answer. A page failing here and "  +
                "there among working ones does not trigger it.",
        ),
        ReleaseNote(
            code = 154,
            name = "0.154",
            header = "Resume works when every chapter is on hold",
            body = "With all your downloads held, the button still offered "  +
                "to pause \u2014 and pausing then unpausing just came back "  +
                "to the same place, so there was no way to start them again "  +
                "from the queue or the notification.\n\nIt now offers "  +
                "Resume all, which releases every hold at once.",
        ),
        ReleaseNote(
            code = 153,
            name = "0.153",
            header = "Pause one download, not all of them",
            body = "Every chapter in the queue now has its own pause, so "  +
                "you can hold one back without stopping the rest \u2014 the "  +
                "queue carries on with whatever is behind it, and a held "  +
                "chapter keeps its place rather than going to the end. It "  +
                "stays held if you close the app.\n\nThe queue's buttons "  +
                "are icons now, and Cancel all asks first, since it "  +
                "discarded everything on a single tap.\n\nAlso: clearing "  +
                "cookies now really does bring a Cloudflare check back, and "  +
                "a series with nothing in it says the source returned no "  +
                "chapters instead of reporting a count of zero.",
        ),
        ReleaseNote(
            code = 152,
            name = "0.152",
            header = "Pause stops the download you are watching",
            body = "Pause only took effect between chapters, so pressing it "  +
                "part-way through one did nothing until that chapter "  +
                "finished \u2014 and against a source that is timing out, "  +
                "that could be a quarter of an hour. It now stops the "  +
                "chapter in flight.\n\nThe chapter stays in the queue and "  +
                "keeps the pages it already had, so resuming carries on "  +
                "rather than starting over.",
        ),
        ReleaseNote(
            code = 151,
            name = "0.151",
            header = "The download queue says which kind of waiting it is doing",
            body = "A download that had not started yet and one that had "  +
                "asked for its pages and received none both said \"Starting\" " +
                "with the same spinner \u2014 which are opposite problems. "  +
                "The queue now separates them, and once the page count is "  +
                "known it counts: \"12 of 36\".\n\nSo a download stuck on "  +
                "\"Fetching pages\" is the source not answering, and one "  +
                "stuck on \"0 of 36\" is the images not arriving.",
        ),
        ReleaseNote(
            code = 150,
            name = "0.150",
            header = "The app no longer closes if the browser view dies",
            body = "Sources protected by Cloudflare are opened in a hidden " +
                "browser view to answer their check. That view runs in its " +
                "own process, and if Android shut it down \u2014 usually to " +
                "reclaim memory \u2014 it took the whole app with it, with no " +
                "warning and nothing in the crash log.\n\nIt now fails the " +
                "one request instead, and says so.",
        ),
        ReleaseNote(
            code = 149,
            name = "0.149",
            header = "A scroll handle on the chapter list",
            body = "The same handle the library got in 0.133, now on a " +
                "series' chapter list \u2014 which on a long-running series is " +
                "the furthest you have to scroll anywhere in the app.\n\nIt " +
                "spans what the filter is showing rather than every chapter " +
                "that exists, so it still reaches the end when you have " +
                "filtered to unread.",
        ),
        ReleaseNote(
            code = 148,
            name = "0.148",
            header = "Chapter changes wait for you",
            body = "Scrolling to the end of a chapter used to flip to the next " +
                "one the instant the transition came into view \u2014 often " +
                "while the list was still flinging, from somewhere you never " +
                "stopped. It now waits until the scroll settles, then pauses " +
                "for half a second.\n\nSo you get to read which chapter is " +
                "next, and scrolling back before it turns cancels it.",
        ),
        ReleaseNote(
            code = 147,
            name = "0.147",
            header = "The double-tap zoom eases in",
            body = "Double-tapping a long strip used to snap straight to 2x " +
                "and straight back. It now travels there over a fifth of a " +
                "second, so you can see where you landed instead of having to " +
                "work it out.\n\nPinching is untouched and still instant \u2014 " +
                "smoothing that would only put it behind your fingers.",
        ),
        ReleaseNote(
            code = 134,
            name = "0.134",
            header = "A better scroll handle",
            body = "Wider, taller, and it stays on screen for a moment after " +
                "you stop scrolling instead of vanishing the instant the list " +
                "settles \u2014 which was usually the moment you were reaching " +
                "for it.\n\nDragging it keeps up now. Every small movement " +
                "used to queue its own jump and the grid worked through them " +
                "in order, so it was always arriving where your finger had " +
                "been rather than where it was.",
        ),
        ReleaseNote(
            code = 133,
            name = "0.133",
            header = "A scroll handle in the library, and free-angle panning",
            body = "The library grid has a scroll handle. It appears while " +
                "you're scrolling and you can drag it to move through a long " +
                "library quickly. It's hidden the rest of the time \u2014 on a " +
                "screen of cover art a permanent one is clutter.\n\nIn long " +
                "strip, dragging diagonally while zoomed now pans sideways " +
                "and scrolls at the same time, instead of ignoring the " +
                "up-and-down half of the drag.",
        ),
        ReleaseNote(
            code = 132,
            name = "0.132",
            header = "Better zoom in long strip",
            body = "Double tap to zoom to 2x on the spot you tapped, and " +
                "double tap again to go back.\n\nDragging at an angle works " +
                "now. 0.131 ignored any drag that was more vertical than " +
                "horizontal, which meant diagonal drags did nothing; it now " +
                "takes the sideways part and leaves the rest to " +
                "scrolling.\n\nPanning is still one-to-one with your finger, " +
                "so crossing a page at 3x takes a couple of drags. Flicking " +
                "to coast is a separate job.",
        ),
        ReleaseNote(
            code = 131,
            name = "0.131",
            header = "Pinch to zoom in long strip",
            body = "Long strip zooms now, up to 3x. Pinch to scale the whole " +
                "chapter, then drag sideways to move across a wide panel.\n\n" +
                "Scrolling is untouched: at normal size nothing about the " +
                "gesture changes, and while zoomed, up and down still scroll " +
                "\u2014 only sideways drags pan. The zoom resets when you open " +
                "another chapter.",
        ),
        ReleaseNote(
            code = 130,
            name = "0.130",
            header = "Chapter transitions in paged mode",
            body = "Paged mode now has the same transition screens long strip " +
                "has had: swipe past the last page to move to the next " +
                "chapter, or back past the first to go to the previous " +
                "one.\n\nThey tell you which chapter you just finished and " +
                "which is next, and say so plainly when there isn't one. " +
                "Tapping them works too.",
        ),
        ReleaseNote(
            code = 129,
            name = "0.129",
            header = "Pull down to refresh a series",
            body = "Drag down on a series' chapter list to re-fetch it. Same " +
                "thing the Refresh row in the \u22ee menu does \u2014 this is just the " +
                "gesture for it.\n\nOther screens don't have it yet.",
        ),
        ReleaseNote(
            code = 128,
            name = "0.128",
            header = "Classify sources nothing else can",
            body = "Settings \u203a Browse \u203a Unclassified sources. Some sources in " +
                "your library aren't installed and aren't in any repository \u2014 " +
                "a fork's own source, or an extension that has since been " +
                "delisted \u2014 so nothing can tell whether they're 18+.\n\nThat " +
                "is why excluding 18+ still left them showing. Switch them on " +
                "here once and the library filter covers them from then on.",
        ),
        ReleaseNote(
            code = 127,
            name = "0.127",
            header = "An 18+ filter in the library",
            body = "The library's Filter tab has an 18+ row. Tap once to show " +
                "only those, twice to hide them, three times to clear \u2014 the " +
                "same as every other filter there.\n\nIt goes by the source a " +
                "series came from rather than by the series itself, because " +
                "that is the only thing a saved entry records. So a source " +
                "carrying both marks everything saved from it, and a source " +
                "nothing has classified yet is left in when you exclude.\n\n" +
                "Separate from the Browse setting on purpose: that one " +
                "shortens the lists you browse, this one filters the library " +
                "you already have.",
        ),
        ReleaseNote(
            code = 126,
            name = "0.126",
            header = "Hide 18+ sources",
            body = "Settings \u203a Browse \u203a Show 18+ sources. Turn it off and " +
                "adult sources and extensions drop out of the Sources list, " +
                "the Extensions list and global search.\n\nIt leaves your " +
                "library alone \u2014 anything already saved stays saved. And it " +
                "isn't a lock: the switch is in plain sight in Settings.",
        ),
        ReleaseNote(
            code = 125,
            name = "0.125",
            header = "Pick an accent colour",
            body = "Settings \u203a Appearance \u203a Accent colour. Six choices, and " +
                "the app repaints as you tap them.\n\nEach one is a pair \u2014 a " +
                "lighter shade for dark mode and a darker one for light \u2014 so " +
                "switching theme keeps it readable either way. Violet is what " +
                "the app has always used, so nothing changes unless you change " +
                "it.",
        ),
        ReleaseNote(
            code = 124,
            name = "0.124",
            header = "Swipe a chapter to bookmark it",
            body = "Swiping right to left on a chapter row now adds or removes " +
                "a bookmark. Swiping left to right still marks it read or " +
                "unread.\n\nBoth directions used to do the same thing, so if " +
                "you were swiping either way to mark chapters read, the " +
                "leftward one has changed under you. The two have different " +
                "colours behind them to tell them apart.",
        ),
        ReleaseNote(
            code = 123,
            name = "0.123",
            header = "Filter and download by bookmark",
            body = "The funnel's Filter tab has a Bookmarked row now, and the " +
                "download menu can queue everything you've bookmarked.\n\n" +
                "Downloading bookmarked chapters ignores whether they're read " +
                "\u2014 a bookmark often means \u201ckeep this one\u201d rather than " +
                "\u201cread this next\u201d. It still skips anything already on " +
                "disk, like the rest of that menu.",
        ),
        ReleaseNote(
            code = 122,
            name = "0.122",
            header = "Undoing 0.121",
            body = "0.121 would not start at all. The size change stripped " +
                "something the app needs to wire itself up on launch, and it " +
                "did so in a way the build could not see \u2014 it compiled " +
                "cleanly and then failed on the phone.\n\nThis release puts " +
                "it back. The app is the size it was before, and everything " +
                "works again. Sorry about that.",
        ),
        ReleaseNote(
            code = 121,
            name = "0.121",
            header = "A smaller app",
            body = "Unused code is stripped out of the build now, which takes " +
                "back most of the size the new icon set added.\n\nNothing " +
                "should look or behave differently. If a source stops " +
                "loading, listing chapters or showing covers after this " +
                "update, that is this change and it is worth reporting \u2014 " +
                "extensions are separate apps and the build can't see what " +
                "they need.",
        ),
        ReleaseNote(
            code = 120,
            name = "0.120",
            header = "Bookmark chapters",
            body = "Long-press a chapter and use Bookmark in the bar that " +
                "appears. Bookmarked chapters carry a marker in the list.\n\n" +
                "Bookmarks are their own thing: reading a chapter, or marking " +
                "it unread, leaves its bookmark alone.\n\nFiltering by " +
                "bookmark, and downloading everything bookmarked, are the next " +
                "update \u2014 this is the piece they were both waiting on.",
        ),
        ReleaseNote(
            code = 119,
            name = "0.119",
            header = "The Library opens on the tab you left",
            body = "It survived rotating the phone but not closing the app, " +
                "which is the case that actually comes up. The tab is " +
                "remembered properly now.",
        ),
        ReleaseNote(
            code = 118,
            name = "0.118",
            header = "Bigger cards in History and Downloads",
            body = "Both tabs now use the same larger cover, and Remove and " +
                "Delete are icons rather than words \u2014 the word was wider " +
                "than the thing it acted on and pushed titles onto two " +
                "lines.\n\nBoth ask before they act now, including Clear all " +
                "in History, which wiped the whole list on a single tap.",
        ),
        ReleaseNote(
            code = 117,
            name = "0.117",
            header = "Share works from the library too",
            body = "0.116's Share row never appeared on a series opened from " +
                "the Library — the screen shows a placeholder while it " +
                "fetches, and the share link was worked out from that instead " +
                "of from the real thing.",
        ),
        ReleaseNote(
            code = 116,
            name = "0.116",
            header = "An options menu on the series screen",
            body = "The last of the series screen work. The \u22ee in the top bar " +
                "holds Refresh, Edit categories and Share.\n\nRefresh re-asks " +
                "the source for the chapter list and leaves the list on screen " +
                "while it does. Share offers the series' page on its own site, " +
                "and only appears when there is one.\n\nThat completes the top " +
                "bar: download, filter and options, all where Mihon puts them.",
        ),
        ReleaseNote(
            code = 115,
            name = "0.115",
            header = "Download the next few chapters",
            body = "A download button in the series screen's top bar: next " +
                "chapter, next 5, 10, 25, or every unread one.\n\nIt skips " +
                "anything already on your device, so \u201cnext 5\u201d means the " +
                "next five you haven't got rather than the next five rows \u2014 " +
                "and it follows whatever sort and filter you have on screen.",
        ),
        ReleaseNote(
            code = 114,
            name = "0.114",
            header = "Badges in History and Downloads",
            body = "The unread count and the read dimming now appear on the " +
                "History and Downloads tabs as well, so every screen that " +
                "lists series marks them the same way.\n\nNo DL chip on the " +
                "Downloads tab \u2014 everything there is downloaded, so it " +
                "would be a badge that is always on and tells you nothing.",
        ),
        ReleaseNote(
            code = 113,
            name = "0.113",
            header = "Badges while browsing",
            body = "Browsing a source and searching every source now show the " +
                "same corner markers the library does: an unread count, a DL " +
                "chip for anything downloaded, and dimming for series filed " +
                "under Read.\n\nOnly series already in your library have any " +
                "of that to show, which is the point \u2014 it tells you what " +
                "you already have without opening anything.\n\nHistory and " +
                "Downloads are next.",
        ),
        ReleaseNote(
            code = 112,
            name = "0.112",
            header = "Chapter numbers actually work now",
            body = "Sorting by chapter number did nothing in 0.111, and " +
                "titling rows by number looked identical to titling them by " +
                "name. Both had the same cause: almost no source publishes a " +
                "chapter number, so there was nothing to sort or show.\n\n" +
                "The number is now read out of the chapter's own title, the " +
                "same way Mihon does it \u2014 volume and version tags are " +
                "ignored, and half chapters, extras and specials land just " +
                "after the chapter they follow. A chapter with no number " +
                "anywhere in its title still keeps its name and still sorts " +
                "to the end.\n\nThe accent colour is a little darker.",
        ),
        ReleaseNote(
            code = 110,
            name = "0.110",
            header = "Filter, sort and display chapters",
            body = "The funnel in the series screen's top bar opens three tabs. " +
                "Filter to downloaded or unread chapters, sort by source order, " +
                "chapter number, upload date or name in either direction, and " +
                "title rows with the chapter number instead of its name.\n\n" +
                "The funnel lights up while a filter is on, and the count above " +
                "the list says \u201cN of M\u201d, so a shorter list never looks like " +
                "chapters going missing.\n\nThese are one setting for the whole " +
                "app rather than per series, like the reader's are. Bookmarks " +
                "and filtering by scanlator aren't here yet.",
        ),
        ReleaseNote(
            code = 109,
            name = "0.109",
            header = "A top bar on the series screen",
            body = "The back arrow used to scroll away with the cover. It now " +
                "sits in a bar that stays put, and the bar fades in with the " +
                "series title as you scroll down \u2014 so the cover art is still " +
                "uncovered when you open the page.\n\nGroundwork as much as " +
                "anything: the download, filter and options buttons go in that " +
                "bar over the next few updates.",
        ),
        ReleaseNote(
            code = 108,
            name = "0.108",
            header = "Real icons",
            body = "Three buttons have been wearing the wrong glyph because the " +
                "icon pack this app shipped with only has about forty of them. " +
                "Download is a download arrow, pinning a source is a pin " +
                "instead of a star, and the library's filter button is a " +
                "funnel instead of a hamburger.\n\nCosts about a megabyte of " +
                "app size, which is why it waited for a reason to be worth it.",
        ),
        ReleaseNote(
            code = 107,
            name = "0.107",
            header = "Groundwork for chapter sorting",
            body = "Chapters now carry the number their source published, " +
                "which is what a sort by chapter number needs. Nothing looks " +
                "different yet \u2014 the sorting itself is the next release.\n\n" +
                "Chapter lists saved for offline reading before this update " +
                "have no number stored, so they'll pick one up the next time " +
                "the series is opened online.",
        ),
        ReleaseNote(
            code = 106,
            name = "0.106",
            header = "Scroll on to the next chapter",
            body = "Scroll a strip past its last page and through the row at " +
                "the end, and the next chapter opens \u2014 scroll up past the " +
                "first page for the previous one. Tapping the row still works.",
        ),
        ReleaseNote(
            code = 105,
            name = "0.105",
            header = "Chapter transitions at each end of a strip",
            body = "Long strip mode now ends with a row telling you what you " +
                "just finished and what's next, and starts with one for the " +
                "previous chapter. Tap it to go there.\n\nIt says so when " +
                "there isn't a next chapter, rather than just stopping.\n\n" +
                "0.101 and 0.102 both claimed you could scroll past the end to " +
                "change chapter. Neither actually worked, and that attempt has " +
                "been removed \u2014 the row replaces it.",
        ),
        ReleaseNote(
            code = 103,
            name = "0.103",
            header = "The chapter swipe is smooth now",
            body = "It was built on a component meant for deleting rows, which " +
                "had to be talked out of deleting anything on every swipe \u2014 " +
                "hence the misfires and the swipes that did nothing.\n\nIt now " +
                "uses the same library Mihon and TachiyomiSY use for this exact " +
                "row, so the icon follows your finger and the action fires once, " +
                "at the point you'd expect.",
        ),
        ReleaseNote(
            code = 102,
            name = "0.102",
            header = "Chapter transitions actually work now",
            body = "Scrolling past the end of a strip did nothing in 0.101 \u2014 " +
                "the stretch effect at the edge of the list was swallowing the " +
                "gesture before the reader saw it.\n\nChanging chapter also " +
                "no longer flashes the series page on the way through.",
        ),
        ReleaseNote(
            code = 101,
            name = "0.101",
            header = "Scroll past the end of a chapter to reach the next one",
            body = "In long strip mode, keep scrolling past the bottom of a " +
                "chapter and the next one opens; scroll up past the top for " +
                "the previous one. Paged modes keep the Prev and Next buttons " +
                "\u2014 the same gesture there belongs to page turning.\n\n" +
                "Swipe-to-mark-read also needs a longer swipe now, so it stops " +
                "firing by accident while scrolling.",
        ),
        ReleaseNote(
            code = 100,
            name = "0.100",
            header = "Swipe a chapter to mark it read",
            body = "Swipe a chapter row either way to mark it read, or unread " +
                "if it already was. The row springs back \u2014 nothing is " +
                "removed.\n\nAlso fixed: Refresh what's missing left the " +
                "library looking as though a " +
                "full refresh had been stopped part way, offering to resume " +
                "something that never ran. It no longer claims the resume " +
                "point.\n\nIf your Chapter counts row is offering Resume " +
                "refresh and you didn't stop one, that's this \u2014 resuming " +
                "is harmless and will simply refresh the rest of the library.",
        ),
        ReleaseNote(
            code = 99,
            name = "0.99",
            header = "Refresh only the series that were missed",
            body = "A refresh skips series whose extension isn't installed. " +
                "Install it later and the only way to pick them up was another " +
                "full sweep of the whole library.\n\nSettings \u203a Library " +
                "now offers Refresh what's missing, which fetches just the " +
                "series that still have no chapter count \u2014 the skipped " +
                "ones and the failed ones.",
        ),
        ReleaseNote(
            code = 98,
            name = "0.98",
            header = "Refresh one source, and separate credits",
            body = "A refresh can now cover just the sources you pick rather " +
                "than all 3575 series \u2014 Settings \u203a Library \u203a " +
                "Refresh some sources. Handy when the summary names one that " +
                "failed.\n\nSeries also keep the writer and the artist apart " +
                "instead of running them together, and tapping either one " +
                "searches for that person alone.",
        ),
        ReleaseNote(
            code = 97,
            name = "0.97",
            header = "Restores two fixes that went missing",
            body = "0.94 and 0.96 each quietly undid an earlier fix. Resume " +
                "went back to opening the first unread chapter instead of the " +
                "one you were on, and the library stopped noticing covers that " +
                "fail to load, which is half of what repairs them. Both are " +
                "back.",
        ),
        ReleaseNote(
            code = 96,
            name = "0.96",
            header = "Search by title or author from the series page",
            body = "Tapping a series' title, or its author, now searches every " +
                "source for it \u2014 the quickest way to find the same series " +
                "somewhere that is still updating it.\n\nAlso: a few sources " +
                "can't be named by anything else \u2014 ones " +
                "built into another app, or extensions delisted since your " +
                "backup was made. The backup itself carries their names and " +
                "they were being read and thrown away.\n\nIf your library " +
                "still shows Unknown source tabs, re-import the same backup: " +
                "it is a merge, so nothing else changes.",
        ),
        ReleaseNote(
            code = 95,
            name = "0.95",
            header = "Source tabs show names, not id numbers",
            body = "Grouping the library by source listed raw ids. Two things " +
                "were wrong: names are learned in the background just after " +
                "the app opens, and the library had already drawn itself by " +
                "then \u2014 and a source whose extension you have since " +
                "uninstalled had no name recorded anywhere at all. The " +
                "extension list now names every source in the catalogue, so " +
                "open Browse \u203a Extensions once and the rest fill in.",
        ),
        ReleaseNote(
            code = 94,
            name = "0.94",
            header = "Group your library by source",
            body = "The library can now be grouped by source, alongside " +
                "categories and ungrouped \u2014 it is in the Group tab of the " +
                "library's options sheet.\n\nThis needed the app to remember " +
                "what each source is called, which also fixes the refresh " +
                "summary: when a source fails repeatedly it now names it " +
                "instead of printing an internal id.",
        ),
        ReleaseNote(
            code = 93,
            name = "0.93",
            header = "Marking a chapter unread forgets your page in it",
            body = "It only cleared the read tick and kept the page you had " +
                "reached, so a chapter you had deliberately reset still " +
                "counted as the furthest one you had started \u2014 and Resume " +
                "sent you back to it.",
        ),
        ReleaseNote(
            code = 92,
            name = "0.92",
            header = "The refresh reports repaired covers after a stop",
            body = "It only showed how many covers it had fixed once a sweep " +
                "ran all the way to the end, and the count was thrown away " +
                "when the app closed \u2014 so stopping a refresh, which is " +
                "when you would look, showed nothing. It is now saved with the " +
                "rest of the summary and shown either way.",
        ),
        ReleaseNote(
            code = 91,
            name = "0.91",
            header = "Resume goes where you actually stopped",
            body = "The Resume button aimed at the first chapter you hadn't " +
                "read, which on an imported library is usually near the start " +
                "\u2014 so a series you were 50 chapters into opened at chapter " +
                "3. It now goes to the furthest chapter you've made progress " +
                "in, or the next one after it if you finished it.\n\nCover " +
                "size in Settings also stops claiming it affects the library " +
                "grid; the grid's columns are set in its own options sheet.",
        ),
        ReleaseNote(
            code = 90,
            name = "0.90",
            header = "The library repairs its own covers",
            body = "Covers were stored once, when a series was added, and never " +
                "re-checked \u2014 so entries imported without one stayed grey " +
                "until you opened them individually, and entries whose source " +
                "later moved the file showed a broken image forever.\n\nA " +
                "chapter-counts refresh now fixes both as it goes. It only " +
                "spends a request on entries that need one: covers that are " +
                "missing, and covers the library grid has actually watched " +
                "fail. Settings \u203a Library \u203a Chapter counts, and the " +
                "row reports how many it fixed.",
        ),
        ReleaseNote(
            code = 89,
            name = "0.89",
            header = "Fixes to yesterday's resume change",
            body = "Reopening a chapter you'd backed out of mid-download " +
                "showed every page as failed for a few seconds. The old " +
                "download was still shutting down and switched the \u201cstill " +
                "loading\u201d light off after the new one had switched it " +
                "on.\n\nScrolling backwards is quick now too \u2014 the pages " +
                "just behind you are fetched before the ones at the start of " +
                "the chapter, rather than last.\n\nAnd the right-to-left page " +
                "slider fills from the correct end; 0.88 fixed which way it " +
                "dragged and left it looking full at page one.",
        ),
        ReleaseNote(
            code = 88,
            name = "0.88",
            header = "Resuming a chapter loads the page you're on first",
            body = "Picking a chapter back up used to start downloading from " +
                "page 1, so resuming at page 40 meant waiting through 39 pages " +
                "nobody was looking at. It now fetches from where you stopped " +
                "to the end of the chapter, then goes back for the earlier " +
                "pages.\n\nAlso: in right-to-left mode the page slider was " +
                "mirrored \u2014 dragging it the way the pages turn moved " +
                "backwards through the chapter. And the chapter list in the " +
                "reader now opens at the chapter you're reading instead of at " +
                "the top of the series.",
        ),
        ReleaseNote(
            code = 87,
            name = "0.87",
            header = "The Sources list actually keeps its place now",
            body = "0.86 said it did and it didn't. Opening a source records " +
                "it as the last one used, which is part of how that list is " +
                "ordered \u2014 so the app decided the order had changed and " +
                "threw the position away, on exactly the trip it was meant to " +
                "survive.",
        ),
        ReleaseNote(
            code = 86,
            name = "0.86",
            header = "Three from the bug board",
            body = "Stop on a series screen cancelled every download in the " +
                "queue, not just that series \u2014 and it read \u201cStop\u201d " +
                "on every series screen whenever anything anywhere was " +
                "downloading. It now shows only when this series has chapters " +
                "queued, and stops only those. Cancel all is still on the " +
                "download queue screen.\n\n" +
                "The Sources list keeps its place when you open a source and " +
                "come back.\n\n" +
                "Sources with filters but no Latest listing \u2014 Roku Hentai " +
                "is one \u2014 showed a Filter chip and no Popular chip, so " +
                "filtering was a one-way trip. Popular is always there now.",
        ),
        ReleaseNote(
            code = 85,
            name = "0.85",
            header = "Asura Scans from the library, and downloads",
            body = "Opening an Asura Scans series from the library showed a " +
                "long error about a null object and an out-of-date chapter " +
                "list, and downloading anything from it failed the same way. " +
                "From Browse the same series was fine.\n\n" +
                "The scratch note 0.83 added was allowed to be absent, and " +
                "extensions are built expecting it never to be. Every one of " +
                "them reads it without checking, so \u201cabsent\u201d " +
                "crashed instead of taking the fallback the extension had " +
                "written for exactly that case. It is now empty rather than " +
                "missing, and those fallbacks run.",
        ),
        ReleaseNote(
            code = 84,
            name = "0.84",
            header = "Chapters that read fine but wouldn't download",
            body = "On some sources every queued chapter failed with a long " +
                "error about a null object, while the same chapters opened " +
                "and read perfectly.\n\n" +
                "The download queue stores chapters by id and rebuilds them " +
                "when its turn comes, and the rebuilt version was missing the " +
                "scratch note 0.83 added \u2014 the extension had one when you " +
                "opened the series and not when the queue got there. The " +
                "queue now asks the source for the real chapter when its own " +
                "copy isn't enough.",
        ),
        ReleaseNote(
            code = 83,
            name = "0.83",
            header = "Asura Scans, and sources like it",
            body = "Newer extensions keep a scratch note attached to each " +
                "series and chapter \u2014 usually the raw data they parsed it " +
                "from \u2014 so they can reuse it later instead of fetching " +
                "the same page twice. This app had nowhere to put it, so those " +
                "sources listed nothing at all.\n\n" +
                "They have somewhere to put it now.",
        ),
        ReleaseNote(
            code = 82,
            name = "0.82",
            header = "Sources that showed nothing at all",
            body = "Some sources \u2014 Asura Scans and SpyFakku among them \u2014 " +
                "listed nothing and reported being built against a newer API " +
                "than the app provides. The cause was a library this app " +
                "shipped, several versions behind what extensions are now " +
                "compiled against, and it affected any source that reads JSON " +
                "from its site.\n\n" +
                "That library is up to date now.",
        ),
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
