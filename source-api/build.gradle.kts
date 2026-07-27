// Flattened from Tachiyomi 0.15.x's KMP source-api module into a plain
// Android library. Coordinates come from tachiyomi-ref's libs.versions.toml.

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
        // OkHttpExtensions.kt uses context receivers.
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

    api(platform("com.squareup.okhttp3:okhttp-bom:5.0.0-alpha.12"))
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
