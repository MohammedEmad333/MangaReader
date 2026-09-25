package com.mangareader.app

/** Release-note chunk 118 down to 92, newest first. */
internal val releaseNotes118To92: List<ReleaseNote> = listOf(
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
)
