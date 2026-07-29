plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mangareader.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mangareader.app"
        minSdk = 24
        targetSdk = 34
        // Bumped on every push. versionName tracks versionCode ("0.<code>"), and
        // versionCode has to keep increasing or Android refuses the APK as an
        // upgrade - the installed build is replaced in place, so a repeat or a
        // decrease silently leaves the old one on the phone.
        versionCode = 59
        versionName = "0.59"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = rootProject.file("release.keystore")
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: ""
            keyAlias = "yomu"
            keyPassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: ""
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("me.saket.telephoto:zoomable-image-coil:0.14.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    // Automatic backups. Self-initialises through androidx.startup, so there is
    // no Configuration.Provider or manifest entry to add - the only reason it's
    // here rather than a check on app start is that a schedule which only fires
    // when the app is opened isn't a schedule.
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // :source-api has this as `implementation`, so it isn't on this module's
    // compile classpath. Needed here to build the PreferenceScreen that
    // ConfigurableSource.setupPreferenceScreen() populates.
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation(project(":source-api"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
