package com.mangareader.app

/** Release-note chunk 210 down to 183, newest first. */
internal val releaseNotes209To183: List<ReleaseNote> = listOf(
        ReleaseNote(
            code = 210,
            name = "0.210",
            header = "Downloads restored, Browse fixed, and faster reader controls",
            body = "The Downloads tab can now recover existing downloaded series from " +
                "the readable download-path index instead of incorrectly saying nothing " +
                "is downloaded while storage still contains chapters. Browse → Sources " +
                "now lays its media filters above the source list instead of overlapping " +
                "the list and leaving the page looking blank. The reader also includes " +
                "the new long-strip page gap, per-series reader settings, safer delete " +
                "confirmation, theme-startup polish, and tap-zone page navigation.",
        ),
        ReleaseNote(
            code = 209,
            name = "0.209",
            header = "Anime polish, safer migration and better global search",
            body = "Anime playback now has cleaner picture-in-picture behavior, " +
                "immersive fullscreen, retry on playback errors, durable resume progress, " +
                "better audio focus, a scrollable stream picker, visible rewatch " +
                "progress, and more consistent watched/unwatched state. " +
                "Global search can filter Both, Manga, or Anime, library entries " +
                "identify anime at a glance, migration stays within the same media type, " +
                "and bulk actions avoid " +
                "non-downloadable sources with clearer messages.",
        ),
        ReleaseNote(
            code = 208,
            name = "0.208",
            header = "Anime playback, resume, history, subtitles and PiP",
            body = "Anime extensions now open episodes in the built-in video player " +
                "instead of the manga reader. Playback remembers where you stopped, " +
                "marks completed episodes watched, appears in History, supports " +
                "subtitle tracks, and can continue in picture-in-picture while you " +
                "use another app. Anime and manga sources can also be filtered " +
                "separately, and anime screens use episode/stream wording throughout.",
        ),
        ReleaseNote(
            code = 207,
            name = "0.207",
            header = "Downloads survive source changes and keep their covers",
            body = "Downloaded chapters are reconciled when a source changes its " +
                "chapter IDs, so existing offline chapters stay attached instead " +
                "of looking missing. Series covers are now saved alongside " +
                "downloads too, so downloaded entries can keep their artwork " +
                "available offline.",
        ),
        ReleaseNote(
            code = 206,
            name = "0.206",
            header = "Migrate a series to another source",
            body = "When a source goes dead or loses a series, open the series' " +
                "menu and pick “Migrate to another source.” That searches every " +
                "source for the title; tap the one you want and the entry moves " +
                "there, carrying its categories and its read progress (matched " +
                "by chapter number), and the old copy is removed.",
        ),
        ReleaseNote(
            code = 205,
            name = "0.205",
            header = "Mark read and download a whole selection",
            body = "Long-press in the library to select series, then use the " +
                "overflow menu in the selection bar to mark them all read or " +
                "unread, or queue every chapter for download — alongside the " +
                "category and remove actions that were already there. Chapters " +
                "come from the offline cache where a series has one; any that " +
                "can't be loaded are skipped and counted so nothing fails " +
                "silently.",
        ),
        ReleaseNote(
            code = 204,
            name = "0.204",
            header = "Global search remembers your recent queries",
            body = "Searching every source at once now keeps a short list of " +
                "your recent searches. When the search box is empty they show " +
                "up as chips underneath it, so re-running a search is one tap " +
                "instead of retyping. Tap a chip to run it again, the × to " +
                "forget one, or Clear to drop them all.",
        ),
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
)
