// Flattened from Tachiyomi 0.15.x's KMP source-api module into a plain
// Android library. commonMain + androidMain were merged into src/main.
// Coordinates below are taken verbatim from tachiyomi-ref's
// gradle/libs.versions.toml — do not "modernise" them; the extensions
// targeting lib 1.4 were built against exactly these.

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
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
    }

    sourceSets {
        getByName("main") {
            // The network package was copied from core/src/main/java.
            java.srcDirs("src/main/kotlin", "src/main/java")
        }
    }
}

dependencies {
    // `api` not `implementation`: the app module needs to see these to
    // register Injekt bindings and to talk to sources directly.
    // Tachiyomi's own build file made the same choice.
    api("com.github.inorichi.injekt:injekt-core:65b0440")
    api("io.reactivex:rxjava:1.3.8")
    api("org.jsoup:jsoup:1.17.2")

    // okhttp 5.0.0-alpha.12 per the catalog. This outranks the app's 4.12.0,
    // so Gradle will upgrade the whole build to 5.x — expected, not a mistake.
    api(platform("com.squareup.okhttp3:okhttp-bom:5.0.0-alpha.12"))
    api("com.squareup.okhttp3:okhttp")
    api("com.squareup.okhttp3:logging-interceptor")
    api("com.squareup.okhttp3:okhttp-brotli")
    api("com.squareup.okhttp3:okhttp-dnsoverhttps")
    implementation("com.squareup.okio:okio:3.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.preference:preference-ktx:1.2.1")

    // Used throughout the copied network package.
    implementation("com.squareup.logcat:logcat:0.1")

    // JavaScriptEngine.kt needs this. If you delete that file, drop this too.
    implementation("app.cash.quickjs:quickjs-android:0.9.2")
}
