# Session handoff — 0.70, two bugs off the board

Written 2026-07-30, daytime, after the overnight session that produced 0.69 and
the sweep-2 measurement. **0.70 is written and uncompiled.** Nothing in it has
been built or run.

This session did three things: verified 0.69 on device, measured the sweep-2
arithmetic properly (it was short by 547, not balanced — see
`SESSION_HANDOFF_0.69.md` §2), and wrote 0.70 against two items from the bug
board. Companion to `SESSION_HANDOFF_0.69.md`, which is now a closed record.

---

## 0. Read this before touching anything

Two items, in order.

**1. Confirm the push landed and CI went green.**

```
git log --oneline -3
gh run list --limit 3
```

The last commit this session *intended* was 0.70 —
"Send source headers with cover requests; cache the extension index", six files
on top of `44f6f00`. If `git log` doesn't show it, the push didn't complete and
the working tree may still hold uncommitted changes; `git status --short` will
say. **A red build is the expected failure here, not a surprise** — see §3.

**2. The covers fix is a hypothesis, and the test that confirms it has not been
run.** This is the single most important thing on this page. §1 explains why it
matters more than it sounds like it should.

---

## 1. The covers fix might be fixing nothing, and one test settles it

**What was observed:** the board item "Roku Hentai has no covers", with a
screenshot showing the browse grid — every title parsed and displayed correctly
("Fanbox", "RKGKのおまけ", "Dec mid", and a Chinese title), every cover a plain
grey box.

**What was reasoned:** `App.newImageLoader` already routes Coil through the
shared client, so covers carry the User-Agent, the cookie jar and the Cloudflare
interceptor. They do **not** carry `Referer`, because Referer is built by
`HttpSource.headersBuilder()` from the source's own `baseUrl` — it belongs to the
source, not the client. Page images go out through `http.getImage(page)` in
`TachiyomiSourceAdapter`, which builds the request from the source and keeps it.
Covers go out as `AsyncImage(model = "https://…")`, a bare URL with no source
attached. So the app is asymmetric, and a hotlink-protected site answers 403 to
the covers while serving the pages fine. That matches the symptom exactly.

**What was not done: looking at the actual error.** `Ui.kt`'s `CoverImage`
already captures Coil's throwable and, in debug builds only, renders it over the
grey box together with the last 48 characters of the URL. That overlay exists
precisely for this — its comment says a 403, a 404, an unresolvable host and an
undecodable format all look identical from the outside, and names guessing at a
fix and shipping it blind as the thing to avoid.

**That is what 0.70 did.** The reasoning is sound and the asymmetry is real, but
the diagnosis rests on a symptom match, not on an observation.

**The test, before trusting 0.70:**

Install a **debug** build, open Roku Hentai's browse grid, read the red text on
the grey boxes.

| What the overlay says | Meaning |
|---|---|
| 403 | The hypothesis is right. 0.70 fixes it. |
| 404, or a URL that looks wrong | The cover **parse** is the bug. 0.70 is a no-op and the extension or `coverModel` is where to look. |
| Nothing at all — no red text | Coil was never asked. `thumbnail_url` is null; the source isn't supplying covers and this is a parse bug too. |

The third row is the one worth naming, because it is invisible in a release
build and looks identical to the other two. A source that supplies no cover and
a source whose covers 403 are the same grey box.

**If it turns out to be a parse bug**, 0.70 is still worth keeping — the
asymmetry it fixes is real and will bite some other source — but the changelog
entry claims it fixes this report, and that claim would be wrong. Amend it.

---

## 2. What 0.70 changes

Six files. `versionCode` 69 → 70, `versionName` "0.70", and a `WhatsNew` entry,
per the rule in `WhatsNew.kt`'s own KDoc.

### Covers — `CoverHeaders.kt` (new) and `App.kt`

- A `host → HttpSource.headers` table, built once from
  `SourceManager.listAllSources()`, consulted by an OkHttp interceptor added to
  the image client.
- **Gaps only.** A header Coil or the shared client already set wins; the
  interceptor never overwrites.
- **Host matching walks label boundaries both ways**, so `cdn.example.com`
  matches a source at `example.com` and vice versa. Deliberately not a
  public-suffix lookup: being too loose sends a Referer nobody needed, being too
  tight is the bug. It cannot help a source whose covers live on an unrelated
  domain — that needs the source in scope.
- **Everything is wrapped in `runCatching`.** A lookup failure falls through to
  the bare request, which is today's behaviour.

**Why an interceptor and not a parameter.** The obvious fix is handing
`CoverImage` a source. That works on the browse screens and fails everywhere
else: the library grid, history and global search all draw covers with no single
source in scope, and those are the same screens showing grey boxes. One match
point, no call-site churn, and nothing to remember at the next call site someone
adds.

`SourceManager.invalidateExtensions()` now also drops the header table, so a
newly installed extension's covers carry its Referer immediately rather than
after the next process start.

### Extensions tab — `ExtensionManager.kt`

The board item was "why does it load whenever I open extensions tab". Cause:
`available` is composable-local state in `BrowseScreen.kt:511`, so leaving the
tab drops it and `LaunchedEffect(repos, refreshTick)` refetches on return. The
full Keiyoushi `index.json` is well over a thousand entries, and this path uses
a bare `HttpURLConnection` rather than the shared client — so it doesn't even
get the ten-minute response cache the rest of the app has.

- **The download is cached for ten minutes**, matching `Requests.kt`'s `maxAge`
  so the two caches agree on staleness.
- **The parse is not cached, deliberately.** `isInstalled` and
  `installedVersion` are resolved against the `PackageManager` inside
  `parseIndex`, so a cached parse would keep claiming an uninstalled extension
  is present. Re-parsing a few thousand entries is milliseconds against a
  multi-megabyte download.
- **A failing repo falls back to its last good index** instead of blanking the
  screen.
- `force = true` bypasses the cache, for an explicit user refresh. Install and
  uninstall don't need it — the re-parse already picks up install state.

---

## 3. Why this build is more likely to break than 0.69's was

0.69's risk was one large file checked by brace counting. 0.70's is broader:

- **A brand-new file** that nothing has ever compiled.
- **`okhttp3.Headers` iterated as `Iterable<Pair<String, String>>`** — correct in
  OkHttp 4 and 5, but it is the newest idiom in this codebase.
- **The module version split.** `app` is on okhttp 4.12.0; `source-api` resolves
  `okhttp-bom:5.0.0-alpha.12`. `CoverHeaders` handles an `okhttp3.Headers` that
  crosses that boundary. This is almost certainly fine — `NetworkProbe.kt:77`
  already passes `http.headers` across it — but **it is the first thing to
  suspect if the build fails.**
- `CoverHeaders.interceptor(this)` is called inside `App`'s `okHttpClient { }`
  lambda, which has no receiver, so `this` is the `App` instance. Check that
  first if the error is about `this`.

Nothing here was type-checked. The sandbox that wrote it has no Maven access.

---

## 4. The bug board, as it stands

Four items. Two are addressed by 0.70, both unverified. Two are untouched.

**Addressed:** covers (§1, hypothesis), extensions tab reload (§2, cause
confirmed by reading — this one is not a guess).

**Untouched — "Elite Babes chapters have no pages."** Not investigated. Worth
noting it is the same family as the covers bug and as bug 6: an extension
returning data the app can't use. See §5.

**Untouched — "The app takes some time to open."** Investigated far enough to
name the suspects, not far enough to fix:

- The first `SourceManager.listAllSources()` classloads every installed
  extension APK through `PathClassLoader`. Cached afterwards, so it is a
  cold-start cost specifically, and it scales with how many extensions are
  installed.
- First-parse of the `Library` blob (3571 entries) and of the `SeriesIndex`
  object, both from SharedPreferences, both memoised after the first read, both
  on the main thread during composition.

Both are consistent with the symptom and neither is confirmed. This needs a
trace, not more reading. It is also the one item on the board that a user feels
every single time they open the app.

---

## 5. The thread connecting three of these

Covers that don't load, chapters with no pages, and bug 6's empty chapter lists
are all **an extension returning data the app can't use**, differing only in
which field is missing and which screen shows the gap.

That matters for sequencing. Bug 6's fix — counting empty chapter lists as
failures rather than letting them fall silently into `done` — is not just an
accounting cleanup. It makes the sweep **name the sources that are failing**,
across the whole 3571-entry library, in one pass. Elite Babes may well already
be in that list. Right now the only way to find a broken source is to open it by
hand and notice.

So the order that gets the most information per unit of work is: fix bug 6,
run sweep 3, read the failure list, then work the board against real data
instead of against whichever source happened to get opened.

`SESSION_HANDOFF_0.69.md` §7 item 1 (surface `resumed` in the finished row, and
persist the five counters) is worth doing in the same commit — it is small, and
without it sweep 3's numbers are as hard to trust as sweep 2's were.

---

## 6. State of the tree

- `2bac449` — 0.68, the resume. Seven device checks passed.
- `5ac4ebe` — docs through 0.68.
- `76bd284` — 0.69, index write serialisation and the summary dismiss.
  **Installed; three checks pass** (`SESSION_HANDOFF_0.69.md` §3), with the
  contention gap noted there.
- `44f6f00` — docs: the 547 shortfall recorded, bug numbering reconciled.
- **0.70** — six files, covers and the extension index cache. **Uncompiled,
  uninstalled, and its main claim untested.** Confirm it pushed (§0).

Library is **3571** entries. Sweep 2's arithmetic is short by **547**, split
between bug 3 and bug 6 in unknown proportion.

**Open from the 0.67 review:** bug 3 (counter race) and bug 6 (`done` doesn't
reconcile). Use the seven-item numbering from `SESSION_HANDOFF_0.68.md` §5 —
`SESSION_HANDOFF_0.69.md` §7 has the table and explains the collision that
produced one mislabelled diagnosis already.

**Open, older:** the manifest theme (`SESSION_HANDOFF_0.69.md` §4 — it is not a
one-line fix and four handoffs have said it was), and open thread 1, the reader,
now seven sessions untouched.

**Named next piece of work** stays the Feed / Updates tab, but §5 argues bug 6
should come first and costs little.

---

## 7. If you only do one thing

Install a debug build and read the red text on the grey covers (§1). It is two
minutes, it either confirms or refutes the main claim of the release you just
pushed, and every other decision about the board depends on the answer.
