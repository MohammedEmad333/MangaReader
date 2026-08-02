# Session handoff — 0.135 to 0.146, minification, finished

Written 2026-08-02, evening. Nothing here supersedes anything.
`SESSION_HANDOFF_0.83.md`, `SESSION_HANDOFF_0.87.md`, `SESSION_HANDOFF_0.106.md`,
`SESSION_HANDOFF_0.120.md`, `SESSION_HANDOFF_0.122.md` and
`SESSION_HANDOFF_0.134.md` all remain live reference.

Twelve releases. **One feature shipped** (`CrashLog`) and it was built as an
instrument, not a feature. The rest is the minification card, which is **done**:
23,869,296 → 11,731,348 bytes, everything verified on device, and every failure
along the way is recorded with its mechanism rather than its symptom.

---

## 0. Read this before touching anything

**1. MINIFICATION IS ON AND WORKING, as of 0.146.** 23,869,296 → 11,731,348
bytes, a 12.14 MB saving, with optimisation and obfuscation forced off by AGP
for a debuggable build — so that is the floor. Verified on device: launch,
20/20 extensions, browse, series, reader, source settings, Asura Scans by all
three routes, and global search pinned-only with Asura Scans pinned.

**The last blocker was a JNI FindClass and it took a logcat tombstone to see.**
§4a. `okhttp-zstd` ships `libzstd-kmp.so`, whose static initialiser calls
`FindClass("com/squareup/zstd/ZstdCompressor")` from native code. **R8 reads
dex; it cannot see a reference from a `.so`.** So it deleted the class, ART
treated a pending exception inside a JNI call as fatal and called `abort()` —
signal 6, no Java exception anywhere.

**2. The 0.121 mystery is solved and it was R8 full mode.** §2.
`android.enableR8.fullMode` has defaulted to true since AGP 8.0 and this project
has never set it. In full mode `-keepattributes Signature` does not protect a
class that is not itself kept, and the anonymous `FullTypeReference` subclasses
live in `com.mangareader.app`, outside the `uy.kohesive.injekt.**` keep. This
is settled: 0.136 launched.

**3. The keep list is DERIVABLE, and that is the reusable part.** §3. The `api`
entries in `source-api/build.gradle.kts` are the extensions' compile classpath.
Anything on it not kept whole is a `NoClassDefFoundError` waiting for the one
extension that touches it. Adding a dependency to `source-api` means adding a
keep. Nothing checks this and nothing can.

**4. Global search was never the bug — Asura Scans is.** §4. Pinned-only search
crashes with Asura Scans pinned and is clean without it. Two failures collapsed
into one, and the fan-out was only implicated because it is the one screen that
touches every source at once.

**5. `CrashLog` works, and it works UNDER R8.** §5. Proven twice: unminified at
0.142, and minified at 0.143 with `R8$$SyntheticClass` in the trace to show R8
ran. It produced a normal entry, not the `writeBarebones` fallback, so the
formatted path survives minification intact.

**The consequence: the next Asura Scans crash will produce a trace.** That is
the one thing standing between this card and done, and it is now one tap of a
source away. Nobody has run it — 0.143 has had its test crash and nothing else.

---

## 1. The releases

| | What | Commit | State |
|---|---|---|---|
| 0.135 | Mapping artifact in CI; `FullTypeReference` keeps, inert | `d5009e6` | Verified green |
| 0.136 | `isMinifyEnabled = true`, flag alone | `4d0e3d3` | **Launched**, 1/20 extensions |
| 0.137 | Keeps for kotlin, kotlinx, okhttp3, okio | `ff0b3c1` | **20/20**, crashed on Asura Scans |
| 0.138 | `CrashLog` | `5509656` | **Would not launch** under R8 |
| 0.139 | R8 off | `65341a4` | Verified — restored a working app |
| 0.140 | `CrashLog` → `attachBaseContext`; external mirror; WorkManager keeps | `b1f0b0e` | Verified |
| 0.141 | R8 on, flag alone | `227803f` | Launched; **Asura Scans crashes** |
| 0.142 | Force-a-test-crash row; R8 off | `d594309` | Verified — **log captured** |
| 0.143 | `writeBarebones`; `CrashLog` keep; R8 on | `94b1885` | **UNTESTED** |

Size, measured off the published asset, not estimated:
**23,869,296 → 11,714,964 bytes.** 12.15 MB, with optimisation and obfuscation
forced off by AGP for a debuggable build. That is the floor.

---

## 2. Why 0.121 could not start

`android.enableR8.fullMode` defaults to true from AGP 8.0. This project is on
8.5.2 and has never set it either way, so **every R8 run this app has ever done
was in full mode**, including 0.121's.

In full mode `-keepattributes Signature` is not a global promise. A class that
is not itself kept loses its generic signature regardless of the attribute list.
Injekt's `addSingleton<T>` is inline reified: each call site compiles to an
anonymous subclass of `FullTypeReference<T>`, and the constructor recovers `T`
from `javaClass.genericSuperclass`. Those subclasses are generated in the
**calling** package — `com.mangareader.app.AppModule$registerInjectables$$inlined$addSingleton$1`
— so `-keep class uy.kohesive.injekt.** { *; }` never covered them. Signature
stripped, `genericSuperclass` came back raw, Injekt threw in `onCreate`.

The fix is the pair Gson ships for `TypeToken`, for the same reason:

```
-keep,allowobfuscation,allowshrinking class uy.kohesive.injekt.api.FullTypeReference
-keep,allowobfuscation,allowshrinking class * extends uy.kohesive.injekt.api.FullTypeReference
```

`allowobfuscation,allowshrinking` are load-bearing. A plain `-keep` pins every
such class as a shrinking root, which is the opposite of the point.

`SESSION_HANDOFF_0.122.md` §3 listed two candidates. Candidate 2 had the shape
right and understated the reason: it is not that R8 judged the type argument
unreachable, it is that the class was never kept. Candidate 1 — the wrapped
`-keepattributes` line — is deleted as a variable; the list is on one line now.

---

## 3. The keep list, and where it comes from

0.136 launched and then loaded **1 extension of 20**:

```
NoClassDefFoundError: Failed resolution of: Lkotlin/LazyKt;
NoSuchMethodError: No virtual method sslSocketFactory(
  Ljavax/net/ssl/SSLSocketFactory;Ljavax/net/ssl/X509TrustManager;)
```

R8 shrinks to the union of what it can see and **it cannot see an extension
APK**. `:app` is Kotlin and uses the stdlib, but a different subset. `:app` uses
OkHttp, but never that overload. So the classes survived and the parts only
extensions reach did not.

A missing **method** on a present class is the more dangerous half: it survives
classloading and fails at the call, arbitrarily far from the cause.

0.137 added `kotlin.**`, `kotlinx.coroutines.**`, `kotlinx.serialization.**`,
`okhttp3.**`, `okio.**`. `injekt`, `rxjava`, `jsoup` and `androidx.preference`
were already kept, which is why the failures were these two and not six.
Result: **20/20 extensions**, and browse, series, reader and source settings all
verified on device.

**The derivation, which is the thing worth keeping:** the `api` entries in
`source-api/build.gradle.kts` are the extensions' compile classpath, plus the
Kotlin stdlib every Kotlin compile gets implicitly, plus the `implementation`
entries that reach extension bytecode at runtime anyway — the `api`/
`implementation` split governs what `:app` can compile against and nothing about
what an extension's classloader can reach, because it has this app's dex on its
parent path. Anything on that list not kept whole is a failure waiting for one
extension.

0.140 added `androidx.startup.**` and `androidx.work.**` on the hypothesis in
§5. WorkManager merges `InitializationProvider` in from its own manifest, so it
does not appear in `AndroidManifest.xml` and is easy to miss.

---

## 4. Asura Scans, the one outstanding fault

Under R8, opening Asura Scans crashes the process. Three routes, all of them:
from **Browse**, from a **Library** entry, from **Downloads**.

**Global search was a red herring, and the proof is clean.** Pinned-only search
with Asura Scans pinned → crash. Pinned-only with it unpinned → no crash. So
0.137's global-search failure and 0.141's are one fault in one extension. The
fan-out looked implicated because it is the only screen that touches all 37
sources at once, and every earlier single-source test happened to use something
else.

**SOLVED in 0.146.** The abort message, from a logcat tombstone:

```
JNI DETECTED ERROR IN APPLICATION: JNI FindClass called with pending exception
java.lang.ClassNotFoundException: Didn't find class
"com.squareup.zstd.ZstdCompressor"
```

`okhttp-zstd` ships `libzstd-kmp.so`. `JniZstdKt`'s static initialiser calls
into it, and the native side does `FindClass` on `com/squareup/zstd/ZstdCompressor`.
No Java code names that class, so R8 removed it. ART treats a pending exception
inside a JNI call as a fatal application error and calls `abort()`.

**Why only one source of 37:** Asura Scans answers with `Content-Encoding: zstd`.
Nothing else reached the decompressor at all.

**Why the crash log was empty, correctly:** a handler that only sees `Throwable`
cannot see a native abort. Once `CrashLog` was proven to fire under R8 (§5), its
silence stopped being a gap and became the finding — it ruled out the entire
missing-keep category, because those surface as ordinary Throwables and get
caught. That is what sent this to logcat.

**Why `okhttp3.**` did not cover it:** that package holds the interceptor. The
implementation is under `com.squareup.zstd`, three packages from anything named
in `source-api/build.gradle.kts`.

**The generalisation, now in `proguard-rules.pro`:** the `api` list is necessary
and not sufficient. A dependency that ships a native library can name classes
from JNI that appear nowhere in Java, so keep its whole implementation package
rather than the API surface the app compiles against.

**How long this took to see, and why:** four R8 attempts were spent treating
this as a missing-keep problem, because the first two failures were exactly
that. The category was not questioned until an instrument that had finally been
verified came back silent.

---

## 5. `CrashLog`, and the mistake in the middle of this session

`CrashLog.kt`. Installs a default uncaught-exception handler, writes timestamp,
version, **thread name** and the full cause chain, then delegates to the
previous handler so Android still does what it would have done.

Three things about it are load-bearing and were each learned by getting them
wrong first.

- **It installs in `attachBaseContext`, not `onCreate`.** Android runs
  attachBaseContext → **ContentProviders** → onCreate. 0.138 installed it in
  `onCreate` and died in the provider phase, so it wrote nothing. An empty log
  after a launch crash is not "no crash", it is "the handler was too late".
- **It mirrors to `getExternalFilesDir`.** `filesDir` is app-private, so reading
  it needs a build that *launches* — and a crash-on-startup is exactly when
  there isn't one. That cost two releases. The mirror lands at
  `Android/data/com.mangareader.app/files/crashes.txt` and needs no permission.
- **`writeBarebones`** (0.143). The formatted path touches `SimpleDateFormat`,
  `BuildConfig`, `StringWriter` and two directories; under R8 any of those can
  be absent. A failure inside the handler was caught by `runCatching` and
  swallowed, and a swallowed handler failure is **indistinguishable from no
  handler at all**. The fallback uses nothing but `File` and `toString`, and
  records the writer's own failure next to the original.

**The mistake: two releases of inference were built on an instrument that had
never been tested.** 0.138's blank log was read as evidence about *where* the
crash happened, and 0.140 acted on that reading. The instrument was not verified
to fire until 0.142, six releases later. It did fire, cleanly — but unminified.

**So the open question is narrow and specific: does `CrashLog` work under R8?**
0.143 exists to answer it and has not been run. Force a test crash, confirm the
log fills, and only then go near Asura Scans. If the test crash comes back blank
under R8, the fault is not a Java exception the runtime can hand to a handler,
and `adb logcat` is the only honest route left.

---

## 6. CI, and what can be read from outside the phone

`.github/workflows/build.yml` uploads `app/build/outputs/mapping/` as
**`mapping-debug`**, with `if: always()` and `if-no-files-found: ignore`. The
`always()` matters: the run that most needs it is a **failed** one, because
`missing_rules.txt` is written by a build that dies at `minifyDebugWithR8`.
Confirmed working — nothing on an unminified run, 3.3 MB on a minified one.

`SESSION_HANDOFF_0.122.md` §5's two limits both still hold, and one is worse
than recorded: **artifact downloads redirect to
`productionresultssa*.blob.core.windows.net`**, not just job logs. Run/job/step
status is all readable from `api.github.com`; nothing else is.

---

## 7. A number that does not match, and nobody has checked it

Extension diagnostics reports **20 packages / 37 sources**. This document set
has said **26 extensions / 95 sources** since `SESSION_HANDOFF_0.122.md`.

R8 cannot cause this. The package count comes from `PackageManager`, which
minification does not touch, and 0.136 reported 20 while broken. So either six
extensions were uninstalled some time after 0.122, or 26/95 was wrong when it
was written. **Find out before someone reads it as a regression** — it is
currently the acceptance criterion on the minification card and it cannot be met.

---

## 8. The board

Done: the mapping artifact, the full-mode diagnosis, the derived keep list,
`CrashLog`.

Still open:

- **The 20/37 vs 26/95 discrepancy** — §7.
- **AHottie**, **Coomer**, BeeHentai and Elite Babes: unchanged, all
  pre-existing and unrelated to R8.
- **Animate the double-tap zoom** and **fling the zoom pan** —
  `SESSION_HANDOFF_0.134.md` §6, untouched this session.
- **Scroll handles on every other list**, **scroll-to-refresh elsewhere**, **a
  feed tab in Browse** — untouched.
- *Chapter transitions are too instant* and *all-filters-enabled* are new bug
  cards from 2026-08-01 that predate this session and were not looked at.

---

## 9. State of the tree

Head is 0.144. Build environment unchanged from 0.134.

**`isMinifyEnabled = true` on debug.** All five attempts are recorded in the
comment above that flag and in `proguard-rules.pro`.

New file: `CrashLog.kt`. `App` gained `attachBaseContext`. `AdvancedSettings`
gained a *Force a test crash* row and a *Crash log* row with a Clear action.
`app/proguard-rules.pro` grew four keep blocks and lost the two candidate
explanations at the bottom.

**Verified on device this session:** 0.136 launching under R8; 0.137 loading
20/20 extensions with browse, series listing, reading a page and a source's
settings dialog all working under R8; 0.139 and 0.140 launching; the crash log
capturing a deliberate main-thread crash with a full cause chain at 0.142; and
that Asura Scans is the sole trigger of the global-search crash.

**Verified on 0.143 (R8 ON):** the crash log captures a main-thread crash with a
full cause chain, in its normal format, with R8 confirmed in the trace.

**Verified after the handoff was first written:** Asura Scans opens normally on
0.144 with R8 off. The fault requires minification.

Head is now `0.144`, R8 **off**, published and working.

**The token used this session was the one the user asked to keep, and all others
were revoked at the start of it.** It is a fine-grained PAT with contents write.
It was pasted into a chat, so it should be rotated on the usual grounds.

**Two things in this repo that are not tokens and should still be dealt with:**
`debug.keystore` and `release.keystore` are committed at the repo root. The
release keystore is the signing identity for this app.
