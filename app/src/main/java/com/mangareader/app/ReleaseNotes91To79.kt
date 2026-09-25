package com.mangareader.app

internal val releaseNotes91To79: List<ReleaseNote> = listOf(
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
)
