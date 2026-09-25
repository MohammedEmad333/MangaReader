package com.mangareader.app

/** Release-note chunk 155 down to 119, newest first. */
internal val releaseNotes155To119: List<ReleaseNote> = listOf(
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
)
