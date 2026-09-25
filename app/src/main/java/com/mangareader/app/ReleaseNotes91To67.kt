package com.mangareader.app

/** Release-note chunk 91 down to 67, newest first. */
internal val releaseNotes91To67: List<ReleaseNote> = listOf(
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
)
