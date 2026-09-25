package com.mangareader.app

/** Release-note chunk 182 down to 156, newest first. */
internal val releaseNotes182To156: List<ReleaseNote> = listOf(
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
)
