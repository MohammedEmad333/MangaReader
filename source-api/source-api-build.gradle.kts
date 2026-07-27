// Rewritten from Mihon's version: convention plugins and version-catalog
// aliases replaced with explicit plugins and coordinates, since this project
// has no build-logic module and no libs.versions.toml.
//
// The `implementation(projects.core.common)` line is deliberately dropped.
// Compile, see which tachiyomi.core.common.* imports fail, and shim only
// those rather than vendoring another Mihon module.

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
}

android {
    namespace = "eu.kanade.tachiyomi.source"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        // Keep only if source-api/consumer-proguard.pro actually exists.
        consumerProguardFiles("consumer-proguard.pro")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Versions chosen to match this project's Kotlin 2.0.20 / AGP 8.5.2 /
    // compileSdk 34, NOT the newest available. Newer OkHttp/jsoup would drag
    // compileSdk and AGP up with them.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Injekt is what extension constructors call for NetworkHelper.
    // Coordinate per Mihon's own dependency contract; needs jitpack.
    implementation("com.github.mihonapp:injekt:91edab2317")

    // RxJava 1.x — this is what the 1.4-era Observable methods use.
    implementation("io.reactivex:rxjava:1.3.8")

    implementation("org.jsoup:jsoup:1.18.1")

    // Not in Mihon's file because it arrived via core:common — but HttpSource
    // needs it directly, so it has to be declared here.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("androidx.preference:preference-ktx:1.2.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.runtime:runtime")
}
