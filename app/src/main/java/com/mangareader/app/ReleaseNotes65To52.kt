package com.mangareader.app

/** Release-note chunk 65 down to 52, newest first. */
internal val releaseNotes65To52: List<ReleaseNote> = listOf(
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
)
