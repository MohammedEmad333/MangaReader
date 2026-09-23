plugins {
    id("com.android.application") version "8.5.2" apply false
    // 2.2.21, not 2.0.20, and it is OkHttp that forced it. OkHttp 5.2.0 is the
    // first release carrying okhttp3.CompressionInterceptor, which current
    // extensions construct, and every OkHttp from 5.2.0 on is built with Kotlin
    // 2.2.x. A 2.0 compiler refuses 2.2 metadata outright — the failing build
    // was three copies of "binary version of its metadata is 2.2.0, expected
    // version is 2.0.0" against kotlin-stdlib, pulled in transitively.
    //
    // There is no version of OkHttp that has the class and is readable by
    // Kotlin 2.0, so this was not a choice between two options.
    //
    // All three Kotlin plugin versions move together — the compose and
    // serialization plugins are versioned in lockstep with the compiler, and
    // the serialization one is declared in source-api/build.gradle.kts.
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
}
