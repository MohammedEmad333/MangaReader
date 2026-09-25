package com.mangareader.app

internal val releaseNotes78To67: List<ReleaseNote> = listOf(
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
)
