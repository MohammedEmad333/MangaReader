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
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod,
                RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# ---------------------------------------------------------------------------
# Reflectively instantiated app classes
# ---------------------------------------------------------------------------
# AGP keeps whatever the manifest names — App, MainActivity, DownloadService,
# LibraryRefreshService, the FileProvider. AutoBackupWorker is NOT in the
# manifest: WorkManager constructs it from a stored class name, which is why
# Backup.kt says it is public and top-level on purpose. Nothing in this app
# calls its constructor, so R8 has no reason to think it is reachable, and the
# failure mode is automatic backups silently never running.
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

# RxJava 1.x. The lib 1.4 extension API is Rx-based; most of it is reached only
# from extension bytecode.
-keep class rx.** { *; }
-keep interface rx.** { *; }

# Jsoup. Every ParsedHttpSource extension parses with it.
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
