plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystoreFile = rootProject.file("release.keystore")
val releaseKeystorePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD").orEmpty()

android {
    namespace = "com.mohammedemad333.serieshub.extension.dramacafe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mohammedemad333.serieshub.extension.dramacafe"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "17.2"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseKeystoreFile
            storePassword = releaseKeystorePassword
            keyAlias = "yomu"
            keyPassword = releaseKeystorePassword
        }
    }

    buildTypes {
        getByName("debug") {
            if (releaseKeystoreFile.isFile && releaseKeystorePassword.isNotBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            if (releaseKeystoreFile.isFile && releaseKeystorePassword.isNotBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
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
    compileOnly(project(":source-api"))
}
