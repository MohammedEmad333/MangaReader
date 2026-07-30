// Flattened from Tachiyomi 0.15.x's KMP source-api module into a plain
// Android library. Coordinates come from tachiyomi-ref's libs.versions.toml.

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    // Lockstep with the Kotlin version in the root build file. See the note there.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
}

android {
    namespace = "eu.kanade.tachiyomi.source"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        consumerProguardFile("consumer-proguard.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // OkHttpExtensions.kt's parseAs/decodeFromJsonResponse are `context(Json)`.
        //
        // Kotlin 2.2 deprecated context receivers in favour of context
        // parameters (-Xcontext-parameters), so this flag is living on borrowed
        // time and is the most likely thing to break on the next Kotlin bump.
        // If it stops being accepted, the two functions are the entire usage in
        // the tree and both take Json — converting them to a plain first
        // parameter or a Json receiver is a smaller change than migrating to
        // context parameters.
        freeCompilerArgs += "-Xcontext-receivers"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin", "src/main/java")
        }
    }
}

dependencies {
    // `api` so the app module can see these when registering Injekt bindings.
    api("com.github.mihonapp:injekt:91edab2317")
    api("io.reactivex:rxjava:1.3.8")
    api("org.jsoup:jsoup:1.17.2")

    // 5.4.0, not 5.0.0-alpha.12. Extensions built for current Mihon construct
    // okhttp3.CompressionInterceptor, which landed in OkHttp 5.2.0 (2025-10-07)
    // alongside the zstd module. On alpha.12 the class does not exist, the
    // extension's lazy `client` throws NoClassDefFoundError, and — before 0.76 —
    // that closed the app rather than failing the source.
    //
    // **This version is duplicated in app/build.gradle.kts. Change both.** The
    // app module pins the same BOM so its own okhttp usage can't silently
    // resolve to something older than what extensions are compiled against.
    api(platform("com.squareup.okhttp3:okhttp-bom:5.4.0"))
    api("com.squareup.okhttp3:okhttp")
    api("com.squareup.okhttp3:logging-interceptor")
    api("com.squareup.okhttp3:okhttp-brotli")
    api("com.squareup.okhttp3:okhttp-dnsoverhttps")
    implementation("com.squareup.okio:okio:3.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // OkHttpExtensions.kt calls decodeFromBufferedSource, which lives here.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.squareup.logcat:logcat:0.1")
    // quickjs dropped along with JavaScriptEngine.kt
}
