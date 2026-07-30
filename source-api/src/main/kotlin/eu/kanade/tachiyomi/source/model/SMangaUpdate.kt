package eu.kanade.tachiyomi.source.model

/**
 * The result of a combined details-and-chapters fetch.
 *
 * **Read this before changing the constructor.** The signature is
 * `(SManga, List)` and it is fixed by binary compatibility, not by taste:
 * extensions are compiled against their own copy of this class and call
 * `SMangaUpdate.<init>(SManga, List)` directly. Reordering, adding or removing a
 * parameter makes every extension that constructs one fail with a
 * `NoSuchMethodError` at runtime, which surfaces as a source that browses fine
 * and dies the moment you open anything. It was read out of a real extension's
 * dex rather than guessed.
 *
 * ### Why this exists
 *
 * Current extensions no longer implement `chapterListRequest`/`chapterListParse`
 * or `mangaDetailsRequest`/`mangaDetailsParse`. Elite Babes 1.6.3 declares all
 * six and every one of them is `throw UnsupportedOperationException()`. What it
 * implements instead is a single [eu.kanade.tachiyomi.source.online.HttpSource.getMangaUpdate],
 * which fetches details and chapters together and returns them in one of these.
 *
 * That is a real improvement for sources where both come off the same page —
 * one request instead of two — and it is why the legacy entry points are stubs
 * rather than merely unused.
 *
 * Both fields are nullable because the caller says which halves it wants. Asking
 * for chapters only leaves [manga] null, and that is not an error.
 */
class SMangaUpdate(
    val manga: SManga?,
    val chapters: List<SChapter>?,
)
