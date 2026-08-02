# R8 rules for the DEBUG build type. See the note in app/build.gradle.kts for
# why minification is on debug rather than release: CI builds and publishes
# app-debug.apk, so release-only shrinking would never reach an installed APK.
#
# The point of this file is to recover the ~7 MB that material-icons-extended
# adds. That pack is unused *code* — one property per icon — so shrinking
# removes it and renaming recovers nothing extra.

# ---------------------------------------------------------------------------
# Do not rename anything.
# ---------------------------------------------------------------------------
# This app classloads extension APKs with PathClassLoader and the app
# classloader as parent, so extension bytecode resolves eu.kanade.tachiyomi.*
# by name against classes this APK ships. Renaming any of it reproduces this
# project's oldest bug — NoClassDefFoundError: HttpSource — except arriving as
# a source that loads and dies at first use.
#
# Obfuscation is not worth that risk here: the APK is public on GitHub
# Releases, so there is nothing being protected, and shrinking is where all the
# size win is.
-dontobfuscate

# Keep enough attributes for reflection and kotlinx.serialization to work.
# On one line on purpose. The previous version wrapped after a trailing comma,
# which was one of the two candidate explanations for 0.121 and is now ruled
# out rather than left as a variable. It was never the likely one — Signature
# sits on the first line either way — but it cost nothing to delete.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# ---------------------------------------------------------------------------
# Reflectively instantiated app classes
# ---------------------------------------------------------------------------
# AGP keeps whatever the manifest names — App, MainActivity, DownloadService,
# LibraryRefreshService, the FileProvider. AutoBackupWorker is NOT in the
# manifest: WorkManager constructs it from a stored class name, which is why
# Backup.kt says it is public and top-level on purpose. Nothing in this app
# calls its constructor, so R8 has no reason to think it is reachable, and the
# failure mode is automatic backups silently never running.
# ---------------------------------------------------------------------------
# The pre-onCreate window
# ---------------------------------------------------------------------------
# 0.138 would not launch under R8 and its own crash handler recorded nothing,
# which is itself the evidence: the handler was installed in Application
# .onCreate, and Android runs attachBaseContext -> ContentProviders ->
# onCreate. Something died in the provider phase.
#
# WorkManager merges androidx.startup.InitializationProvider into the manifest
# from its own, so it does not appear in AndroidManifest.xml and is easy to
# forget. It instantiates initializers and workers from stored class names,
# which is the exact pattern R8 cannot see. The AutoBackupWorker keep above
# covers this app's worker and nothing about the machinery that constructs it.
#
# Unconfirmed as the cause of 0.138 - CrashLog now installs in
# attachBaseContext, so the next attempt says rather than implies.
-keep class androidx.startup.** { *; }
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { <init>(...); }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class * implements androidx.startup.Initializer { *; }

-keep class com.mangareader.app.AutoBackupWorker { <init>(...); }

# ---------------------------------------------------------------------------
# Libraries extensions reach for directly
# ---------------------------------------------------------------------------
# R8 only sees what :app references. An extension APK is invisible to it, so
# any class only extensions touch looks dead. Everything below is small next to
# material-icons-extended, so keeping it whole costs almost nothing.

# Injekt. Extension constructors do Injekt.get<NetworkHelper>() and
# injectLazy(), resolved by type at runtime.
-keep class uy.kohesive.injekt.** { *; }

# And this is the pair that 0.121 needed and did not have.
#
# The keep above covers uy.kohesive.injekt.**, which includes FullTypeReference
# itself — but NOT its anonymous subclasses. Those are generated at each
# inlined addSingleton<T> call site and live in the CALLING package:
#
#   com.mangareader.app.AppModule$registerInjectables$$inlined$addSingleton$1
#
# So they were never kept, and under R8 full mode an unkept class does not
# retain its generic signature no matter what -keepattributes asks for. The
# signature is what FullTypeReference reads back via javaClass.genericSuperclass.
#
# allowobfuscation and allowshrinking are load-bearing: without them this pins
# every such class as a shrinking root, which is the opposite of the point of
# turning R8 on. The rule asks R8 to preserve the signature IF it keeps the
# class, not to keep the class.
#
# This is the same rule Gson ships for TypeToken, for exactly the same reason.
-keep,allowobfuscation,allowshrinking class uy.kohesive.injekt.api.FullTypeReference
-keep,allowobfuscation,allowshrinking class * extends uy.kohesive.injekt.api.FullTypeReference

# RxJava 1.x. The lib 1.4 extension API is Rx-based; most of it is reached only
# from extension bytecode.
-keep class rx.** { *; }
-keep interface rx.** { *; }

# Jsoup. Every ParsedHttpSource extension parses with it.
# The Kotlin standard library. Every extension is Kotlin and references the
# stdlib facade classes directly — kotlin.LazyKt for `by lazy`, StringsKt,
# CollectionsKt, jvm.internal.Intrinsics for every null check the compiler
# emits. :app is Kotlin too, but it uses a different SUBSET, and R8 shrinks to
# the union of what it can see. 0.136 died on:
#
#   NoClassDefFoundError: Failed resolution of: Lkotlin/LazyKt;
#
# on five extensions at once, because nothing in :app happened to reference
# that particular facade.
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }

# Coroutines and kotlinx.serialization. Declared `implementation` in
# source-api rather than `api`, which affects what :app can compile against
# and NOTHING about runtime: an extension's classloader has this app's dex on
# its parent path, so every class extension bytecode names has to be present
# here regardless of which Gradle configuration put it in.
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }

# OkHttp and Okio. Extensions build their own clients off the shared one —
# newBuilder(), interceptors, and the two-argument sslSocketFactory overload
# that sources with custom trust managers call. :app calls none of that, so R8
# removed the methods while keeping the class, and 0.136 gave:
#
#   NoSuchMethodError: No virtual method sslSocketFactory(
#     Ljavax/net/ssl/SSLSocketFactory;Ljavax/net/ssl/X509TrustManager;)
#
# A missing METHOD on a present class is the more dangerous half of this:
# it survives classloading and fails at the call.
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# ---------------------------------------------------------------------------
# HOW THIS LIST WAS DERIVED, so the next gap is found by reading rather than
# by installing
# ---------------------------------------------------------------------------
# The `api` entries in source-api/build.gradle.kts ARE the extensions' compile
# classpath, plus the Kotlin stdlib every Kotlin compile gets implicitly, plus
# the `implementation` entries that reach extension bytecode at runtime anyway.
# Anything on that list which is not kept whole here is a NoClassDefFoundError
# or a NoSuchMethodError waiting for the one extension that touches it.
#
# Adding a dependency to source-api means adding a keep here. There is no
# build-time check for this and there cannot be one — the code that would fail
# is in an APK R8 has never seen.

-keep class org.jsoup.** { *; }
-keep interface org.jsoup.** { *; }

# androidx.preference. ConfigurableSource.setupPreferenceScreen() is
# implemented inside the extension, which constructs SwitchPreferenceCompat,
# ListPreference, EditTextPreference and friends. SourceSettings only reads the
# populated screen back, so the subclasses look unreferenced from here.
-keep class androidx.preference.** { *; }

# kotlinx.serialization. The runtime ships its own consumer rules; this covers
# this app's own @Serializable types and the generated serializers, which are
# reached through a synthetic Companion rather than a direct call.
-keepclassmembers class com.mangareader.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$$serializer { *; }

# ---------------------------------------------------------------------------
# Missing classes
# ---------------------------------------------------------------------------
# R8 fails the build on a class that is referenced but absent, even from an
# annotation that has no runtime effect. jsoup 1.17.2's package-info files
# carry JSpecify nullness annotations, and jspecify is a compile-only
# dependency that never reaches the APK:
#
#   Missing class org.jspecify.annotations.NullMarked
#   (referenced from: org.jsoup.helper.package-info and 9 other contexts)
#
# This was the whole of 0.121's first red CI run. If another one of these
# appears, R8 writes the exact rule it wants to
# app/build/outputs/mapping/debug/missing_rules.txt — take it from there
# rather than guessing at the package.
-dontwarn org.jspecify.annotations.**

# ---------------------------------------------------------------------------
# Not kept, deliberately
# ---------------------------------------------------------------------------
# androidx.compose.material.icons.** — the whole point. Every icon actually
# referenced is reachable through an ordinary property read, so R8 keeps those
# and drops the rest.
#
# eu.kanade.tachiyomi.** is kept by :source-api's consumer-proguard.pro rather
# than here, because it belongs with the module it describes.
#
# ---------------------------------------------------------------------------
# What a debuggable build actually does with this file
# ---------------------------------------------------------------------------
# AGP prints, for this configuration:
#
#   BuildType 'debug' is both debuggable and has 'isMinifyEnabled' set to true.
#   Debuggable builds are no longer name minified and all code optimizations
#   and obfuscation will be disabled.
#
# So -dontobfuscate above is belt and braces — AGP has already forced it — and
# the optimiser is off. Shrinking still runs, which is the part that matters
# here, but the size win is smaller than an optimised release build's would be.
# Compare the published APK size against 24 MB before concluding this file is
# doing its job.

# ---------------------------------------------------------------------------
# WHY 0.121 DID NOT START, AND WHAT IS UNRESOLVED
# ---------------------------------------------------------------------------
# 0.121 built green and then crashed in Application.onCreate on every launch:
#
#   IllegalArgumentException: Internal error: TypeReference constructed
#   without actual type information
#     at uy.kohesive.injekt.api.FullTypeReference.<init>(TypeInfo.kt:36)
#     at AppModule$registerInjectables$$inlined$addSingleton$1.<init>
#     at com.mangareader.app.AppModule.registerInjectables(App.kt:27)
#
# Injekt's addSingleton<T> / addSingletonFactory<T> are inline reified. Each
# one compiles to an anonymous subclass of FullTypeReference<T>, and the
# constructor recovers T from javaClass.genericSuperclass. If that returns a
# raw Class instead of a ParameterizedType, the type argument is gone and
# Injekt throws — which is exactly what happened.
#
# So R8 dropped the generic signature of those anonymous classes even though
# this file asks for -keepattributes Signature. 0.135 established why.
#
# THE CAUSE: R8 FULL MODE. AGP 8.0 changed the default of
# android.enableR8.fullMode to true, this project has never set it either way,
# and it is on AGP 8.5.2 — so full mode has been in force for every R8 run this
# app has ever done. In full mode -keepattributes Signature is not a global
# promise: a class that is not itself kept does not retain its signature,
# whatever the attribute list says. Candidate 2 was right about the shape and
# understated the reason — it is not that R8 judged the type argument
# unreachable, it is that the class was never kept in the first place.
#
# The keep above stops at uy.kohesive.injekt.**, and the anonymous subclasses
# are in com.mangareader.app. That is the entire gap, and the two
# -keep,allowobfuscation,allowshrinking rules beside that keep now close it.
#
# Candidate 1 — the wrapped -keepattributes line — is gone as a variable: the
# list is on one line now. It was never likely.
#
# WHAT IS STILL UNKNOWN, and it is not small: whether these two rules are
# SUFFICIENT. Nothing in this app has been past Application.onCreate under R8,
# so every keep aimed at the extension boundary below is still entirely
# untested — 0.121 died before it classloaded a single extension.
#
# AND THIS FAILURE IS INVISIBLE TO CI. 0.121 was green, published a 9.59 MB
# artifact, and would not start. Anything touching minification is launched on
# a device before it is believed. app/build/outputs/mapping/ is uploaded as the
# "mapping-debug" artifact as of 0.135 (build.yml), including on failed runs,
# so missing_rules.txt / seeds.txt / usage.txt are readable without a device.
