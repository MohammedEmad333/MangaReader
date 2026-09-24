// Flattened from Tachiyomi 0.15.x's KMP source-api module into a plain
// Android library. Coordinates come from tachiyomi-ref's libs.versions.toml.

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    // Lockstep with the Kotlin version in the root build file. See the note there.
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "eu.kanade.tachiyomi.source"
    // 36, in step with :app. See the note there.
    compileSdk = 36

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
    api("org.jsoup:jsoup:1.23.2")
    api("org.nanohttpd:nanohttpd:2.3.1")

    // 5.4.0, not 5.0.0-alpha.12. Extensions built for current Mihon construct
    // okhttp3.CompressionInterceptor, which landed in OkHttp 5.2.0 (2025-10-07)
    // alongside the zstd module. On alpha.12 the class does not exist, the
    // extension's lazy `client` throws NoClassDefFoundError, and — before 0.76 —
    // that closed the app rather than failing the source.
    //
    // **This version is duplicated in app/build.gradle.kts. Change both.** The
    // app module pins the same BOM so its own okhttp usage can't silently
    // resolve to something older than what extensions are compiled against.
    api(platform(libs.okhttp.bom))
    api(libs.okhttp)
    api(libs.okhttp.logging)
    api(libs.okhttp.brotli)
    // okhttp3.zstd.Zstd. Extensions build their own client as
    // CompressionInterceptor(Zstd, Gzip), so Zstd has to be resolvable even
    // though nothing in this app asks for zstd itself. Same story as
    // CompressionInterceptor one release earlier: the missing class is not one
    // we use, it is one an extension names.
    api(libs.okhttp.zstd)
    api(libs.okhttp.doh)
    implementation("com.squareup.okio:okio:3.7.0")

    // 1.9.0 in lockstep with :app — see the long note there. Short version:
    // extensions built with a newer serialization plugin omit
    // GeneratedSerializer.typeParametersSerializers(), which is abstract before
    // 1.8, and 1.10+ is built on Kotlin 2.3 whose metadata this project's 2.2.21
    // compiler cannot read. **Both modules and both artifacts move together.**
    implementation(libs.serialization.json)
    // OkHttpExtensions.kt calls decodeFromBufferedSource, which lives here.
    implementation(libs.serialization.json.okio)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(libs.androidx.preference)
    implementation("com.squareup.logcat:logcat:0.4")
    // quickjs dropped along with JavaScriptEngine.kt
}
