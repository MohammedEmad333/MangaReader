# Session handoff — 0.121 to 0.122, the shrink that built green and would not start

Written 2026-08-01, ~19:10. Nothing here supersedes anything.
**§3's "WHY IS NOT ESTABLISHED" is now established: R8 full mode. See
`SESSION_HANDOFF_0.143.md` §2. §6's five-step plan was followed on 2026-08-02
and steps 1-4 all passed; step 5 is where it stands.**
`SESSION_HANDOFF_0.83.md`, `SESSION_HANDOFF_0.87.md`, `SESSION_HANDOFF_0.106.md`
and `SESSION_HANDOFF_0.120.md` all remain live reference.

Two releases, one of them a revert. **No feature shipped.** What this session
produced is a measurement, a diagnosis, and two config files that are correct
enough to be worth keeping and are currently switched off.

---

## 0. Read this before touching anything

**1. Minification is off, and `app/proguard-rules.pro` now exists and is
inert.** `isMinifyEnabled = false` on both build types. The rules file and the
rewritten `source-api/consumer-proguard.pro` are still in the tree on purpose —
they are most of the work, and most of them are right.

**2. A green CI run is not evidence for this class of change.** 0.121 built
clean, published a 9.59 MB APK, and crashed in `Application.onCreate` on every
launch. No build step reaches `onCreate`. **Anything touching R8 must be
launched on a device before it is believed** — and that check has to be
explicit, because the usual signal (green, artifact published, size moved) was
all present and all meaningless.

**3. The flag was on the wrong build type for its entire life, and that is the
most reusable fact here.** `isMinifyEnabled = false` sat inside
`getByName("release")`. `.github/workflows/build.yml` runs `assembleDebug` on
every push and publishes `app-debug.apk`; the release APK is behind a manual
`workflow_dispatch` input. So the APK on the phone has always been the debug
one, and every previous discussion of "turning minification on" — including
`SESSION_HANDOFF_0.120.md` §8 item 1 — was describing a change to a build
nobody installs.

**4. The shrink works, and it is worth more than the estimate.** §2. That is why
this is worth another attempt rather than abandoning.

---

## 1. The releases

| | What | Commit | State |
|---|---|---|---|
| 0.121 | R8 on the debug build type; `proguard-rules.pro`; `consumer-proguard.pro` rewritten | `a9bbf5a` | **CI red** — missing class |
| 0.121 | `-dontwarn org.jspecify.annotations.**` | `e18aae8` | Green, published, **crashed on launch** |
| 0.122 | Minification back off | `3f6f2ef` | Verified — installs and runs |

---

## 2. What the shrink actually recovered

**24 MB → 9.59 MB**, measured off the published `latest` asset rather than
estimated.

`SESSION_HANDOFF_0.120.md` §8 put the figure at "about 7 MB back", on the
grounds that `material-icons-extended` is what took the APK from 17 MB to 24.
The real number is **14.4 MB**, and the result lands *below* the pre-icon-pack
17 MB — so R8 removed a great deal besides the icon pack. Compose, OkHttp and
their transitive dependencies all ship far more than this app calls.

This happened with **optimisation and obfuscation forced off**, which AGP does
for any debuggable build and announces:

```
WARNING: BuildType 'debug' is both debuggable and has 'isMinifyEnabled' set to true.
Debuggable builds are no longer name minified and all code optimizations
and obfuscation will be disabled.
```

So that is the *floor*, from tree-shaking alone. **`-dontobfuscate` in
`proguard-rules.pro` is therefore belt and braces on debug** — AGP has already
forced it — and it stops being redundant the moment anyone moves this to
release.

---

## 3. Why 0.121 would not start

```
java.lang.RuntimeException: Unable to create application com.mangareader.app.App:
java.lang.IllegalArgumentException: Internal error: TypeReference constructed
without actual type information
  at uy.kohesive.injekt.api.FullTypeReference.<init>(TypeInfo.kt:36)
  at com.mangareader.app.AppModule$registerInjectables$$inlined$addSingleton$1.<init>
  at com.mangareader.app.AppModule.registerInjectables(App.kt:27)
  at com.mangareader.app.App.onCreate(App.kt:26)
```

**Injekt recovers its type arguments by reflection over the class hierarchy.**
`addSingleton<T>` and `addSingletonFactory<T>` are inline reified; each call
site compiles to an anonymous subclass of `FullTypeReference<T>`, whose
constructor reads `javaClass.genericSuperclass` and requires a
`ParameterizedType`. R8 dropped the generic signature on those anonymous
classes, `genericSuperclass` came back as a raw `Class`, and Injekt threw.

Three things follow.

- **`-keepattributes Signature` was present and was not enough.** Why is **not
  established.** Two candidates are written into `proguard-rules.pro` at the
  point they would be acted on. The likelier: R8 prunes a generic signature
  whose type argument nothing references in a way it recognises, and these
  classes exist only at inlined call sites. If so the fix is an explicit keep —
  `-keep class * extends uy.kohesive.injekt.api.FullTypeReference` — rather
  than a global attribute request.
- **The blast radius was everything at once.** `App.onCreate` registers all
  three bindings before anything else runs, so this presented as a dead app
  rather than as a broken source. That is *better* than the failure mode the
  keep rules were written to prevent — a source that loads and dies at first
  use, which would have been mistaken for an extension bug. Worth knowing that
  the loud version is the lucky one.
- **The rules aimed at the extension boundary were never tested.** Nothing got
  as far as classloading an extension. Whether `-keep class
  eu.kanade.tachiyomi.** { *; }` and the library keeps are sufficient is still
  entirely unknown.

---

## 4. The first failure, and what it cost nothing

`a9bbf5a` failed at `:app:minifyDebugWithR8`:

```
ERROR: R8: Missing class org.jspecify.annotations.NullMarked
       (referenced from: org.jsoup.helper.package-info and 9 other contexts)
```

jsoup 1.17.2's `package-info` files carry JSpecify nullness annotations;
jspecify is compile-only and never reaches the APK. R8 fails the build on a
referenced-but-absent class even when it has no runtime effect. One
`-dontwarn`, and **R8 writes the exact rule it wants** to
`app/build/outputs/mapping/debug/missing_rules.txt` — take it from there rather
than guessing at the package.

This one behaved well: it was loud, specific, and named in the first ten lines
of the log. Recorded mainly to contrast with §3.

---

## 5. Reading CI from outside the phone

CI status and logs were read from the GitHub Actions API with a short-lived
token, as in the previous session. Two limits found:

- **Job logs redirect to `productionresultssa0.blob.core.windows.net`**, which
  is not `api.github.com` and may be unreachable depending on where you are
  reading from. The run/job/step status is all on `api.github.com`; the log
  body is not.
- **`/check-runs/{id}/annotations` needs `checks:read`**, which a
  contents-scoped PAT does not have. It returns *Resource not accessible by
  personal access token*, which reads like a missing resource rather than a
  missing scope.

**`.github/workflows/build.yml` does not upload `app/build/outputs/mapping/`.**
Adding that is the cheapest single improvement to the next R8 attempt — the
mapping directory holds `missing_rules.txt`, `seeds.txt` and `usage.txt`, which
together answer "what did R8 actually remove" without a device or a log fetch.
Not done this session, deliberately: it is a change that can only be verified
by a build, and this session had already shipped one release that built and
didn't work.

**Revoke the token used here**, along with the previous session's, which
`SESSION_HANDOFF_0.120.md` also flagged and which may still be live.

---

## 6. If this is attempted a third time

In this order.

1. **Upload the mapping directory as a CI artifact.** §5.
2. **Add the explicit `FullTypeReference` keep**, not another attribute request.
3. **Turn `isMinifyEnabled = true` on debug and push.**
4. **Install it and launch it before reading any other signal.** If it opens,
   the Injekt bindings survived — that is the entire content of step 4.
5. **Then** work the extension boundary: open a source, list chapters, read a
   page, open a source's settings dialog, and check the diagnose screen still
   reports 26 extensions / 95 sources. None of that has ever been exercised
   under R8.
6. Only after several green-and-launched releases, consider release builds —
   and price in losing `BuildConfig.DEBUG`, which takes `CoverImage`'s failure
   overlay with it (§6 of `PROJECT_HANDOFF.md`).

**What not to do:** do not turn this on together with anything else. 0.121
changed one build flag and still managed to be un-launchable; a release that
also carried a feature would have made the cause ambiguous.

---

## 7. The board

Unchanged by this session except for housekeeping done at the start of it:

- *Library should remember the tab* → **Done** (shipped 0.119).
- *Add bookmarks* → reworded to **Bookmarked filter and Bookmarked download
  action**, which is the half 0.120 unblocked and the next real piece of work.
- **AHottie has no covers, no tabs, and chapters load forever** is the only
  actionable bug. It is `PROJECT_HANDOFF.md` §7 item 14 coming true — AHottie
  is the one lib 1.6 extension, flagged there as "may fail at runtime". The
  "no tabs" half has the shape of 0.86's Roku Hentai fix (`supportsLatest =
  false` hiding the Popular chip), so it may be two faults rather than one.
- Elite Babes and BeeHentai remain upstream.

There is now a **Minification** card carrying §3 and §6.

---

## 8. State of the tree

Head is `3f6f2ef` (0.122) plus whatever docs commit carries this file. Build
environment unchanged from 0.120.

Two files exist that did not before, both currently inert:

- **`app/proguard-rules.pro`** — `-dontobfuscate`, `-dontwarn` for jspecify,
  keeps for `AutoBackupWorker` (WorkManager instantiates it by name and nothing
  calls its constructor), Injekt, RxJava, jsoup, `androidx.preference` and
  kotlinx.serialization. The two candidate explanations for §3 are written at
  the bottom.
- **`source-api/consumer-proguard.pro`** — rewritten from a narrow rule set to
  `-keep class eu.kanade.tachiyomi.** { *; }`. The old version kept `.model.**`,
  `.online.**` and subclasses of `Source`, and missed
  `eu.kanade.tachiyomi.network.**` (every request every extension makes) and the
  top-level `CatalogueSource` / `ConfigurableSource` / `SourceFactory`
  interfaces that `ExtensionLoader` type-checks against. **It had never once
  been applied**, because consumer rules only take effect when the consuming app
  minifies.

**Verified on device this session:** 0.120's short-chapter long-strip case
(the last item `SESSION_HANDOFF_0.106.md` left unverified — it holds); bookmarks
surviving a backup and restore, which `SESSION_HANDOFF_0.120.md` §7c asserted
from the mechanism and nobody had run; clearing bookmarks over a
fully-bookmarked selection; global search with badges at library scale; cold
start; Edit categories from the Options overflow opened from the Library.
0.122 installs and runs.

**Not verified, and this is the whole of §3:** anything at all under R8.
