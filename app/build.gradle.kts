plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mangareader.app"
    // 36 because okhttp-android 5.4.0's AAR metadata demands it — "requires
    // libraries and applications that depend on it to compile against version
    // 36 or later". compileSdk only says which android.jar we compile against;
    // targetSdk stays 34, so no runtime behaviour changes.
    //
    // AGP 8.5.2's max *recommended* compileSdk is 34, which is a warning rather
    // than a limit, suppressed in gradle.properties. If this build fails inside
    // AAPT2 or resource linking rather than in our own code, that suppression is
    // being asked to cover something it can't, and the answer is the real
    // upgrade: AGP 8.11+ with Gradle 8.13+ (and the gradle-version pin in
    // .github/workflows moves too).
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mangareader.app"
        minSdk = 24
        targetSdk = 34
        // Bumped on every push. versionName tracks versionCode ("0.<code>"), and
        // versionCode has to keep increasing or Android refuses the APK as an
        // upgrade - the installed build is replaced in place, so a repeat or a
        // decrease silently leaves the old one on the phone.
        versionCode = 156
        versionName = "0.156"
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
        // Minification is on DEBUG, which looks backwards and isn't.
        //
        // .github/workflows/build.yml runs `assembleDebug` on every push and
        // publishes app-debug.apk to the "latest" prerelease; the release APK
        // sits behind a manual workflow_dispatch input. So debug is the only
        // build that reaches a phone, and shrinking release — which is where
        // this flag conventionally goes, and where it sat doing nothing while
        // the APK grew from 17 MB to 24 — could never affect an installed one.
        //
        // Doing it here rather than switching the published artifact to
        // release also keeps BuildConfig.DEBUG true, and with it CoverImage's
        // failure overlay, which is the only thing in the app that tells a 403
        // from a cover the source never supplied.
        //
        // The target is material-icons-extended: several MB of unused icon
        // properties, which is code, so shrinking removes it. See
        // proguard-rules.pro — and note it sets -dontobfuscate, because
        // extensions resolve the vendored API by name.
        //
        // isShrinkResources is deliberately NOT on. It is a separate pass with
        // its own failure mode (resources looked up by name), the win here is
        // code rather than resources, and one new thing per release.
        // ---------------------------------------------------------------
        // TURNED BACK OFF IN 0.122. 0.121 shipped this as `true` and the app
        // would not start:
        //
        //   java.lang.RuntimeException: Unable to create application
        //   com.mangareader.app.App: java.lang.IllegalArgumentException:
        //   Internal error: TypeReference constructed without actual type
        //   information
        //     at uy.kohesive.injekt.api.FullTypeReference.<init>
        //     at AppModule$registerInjectables$$inlined$addSingleton$1.<init>
        //
        // Injekt's reified helpers work by creating an anonymous subclass of
        // FullTypeReference<T> and reading T back out of
        // javaClass.genericSuperclass at runtime. That only works while the
        // class keeps its Signature attribute.
        // ---------------------------------------------------------------
        // OFF AGAIN IN 0.139, AND BACK ON FOR GOOD IN 0.146. Attempts three
        // through five, and the two causes they found.
        //
        // 0.136 STARTED - so R8 full mode was the right diagnosis and the
        // FullTypeReference keeps were right - and then loaded 1 extension of
        // 20 (NoClassDefFoundError kotlin.LazyKt; NoSuchMethodError
        // OkHttpClient.Builder.sslSocketFactory). CAUSE TWO: R8 shrinks to the
        // union of what it can see and it cannot see an extension APK. 0.137
        // added keeps for kotlin, kotlinx, okhttp3 and okio and reached 20/20.
        //
        // 0.137 then died on Asura Scans, and so did 0.141. Four attempts
        // treated that as a third missing keep, because the first two failures
        // were exactly that. It was not.
        //
        // CAUSE THREE, found at 0.146 from a logcat tombstone: okhttp-zstd
        // ships libzstd-kmp.so, whose static initialiser does
        // FindClass("com/squareup/zstd/ZstdCompressor") FROM NATIVE CODE. No
        // Java code names that class, so R8 removed it, and ART treats a
        // pending exception inside a JNI call as fatal - abort(), signal 6, no
        // Java exception at any point. That is why the crash log was empty and
        // was right to be: a handler that only sees Throwable cannot see a
        // native abort. Fixed by -keep class com.squareup.zstd.** { *; }.
        // Only Asura Scans hit it because it is the one source of 37 that
        // answers Content-Encoding: zstd.
        //
        // THE RULE, and it is in proguard-rules.pro too: the `api` list in
        // source-api/build.gradle.kts is NECESSARY AND NOT SUFFICIENT. A
        // dependency that ships a .so can name classes from JNI that appear
        // nowhere in dex. Keep its whole implementation package.
        //
        // VERIFIED ON DEVICE AT 0.146: launch, 20/20 extensions, browse,
        // series, reader, source settings, Asura Scans from Browse, Library
        // and Downloads, and global search pinned-only with Asura Scans
        // pinned. 23,869,296 -> 11,731,348 bytes.
        //
        // Full account: SESSION_HANDOFF_0.143.md, closed in
        // SESSION_HANDOFF_0.149.md.
        // ---------------------------------------------------------------
        getByName("debug") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // Left off deliberately. Release is not built on push and not
        // installed, so enabling it would ship an untested R8 configuration to
        // the one artifact nobody exercises. Turn it on once debug has been
        // through a few releases with extensions still working.
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // logging-interceptor, okhttp-brotli and okhttp-dnsoverhttps each
            // ship an identical copy of this, and the merge task refuses to
            // pick one. It is OSGi bundle metadata for a Java 9 multi-release
            // jar — it describes the artifact to an OSGi container, and there
            // is no OSGi container in an APK, so dropping it costs nothing.
            //
            // Excluded by exact path rather than a glob over META-INF: this is
            // the only collision, and a broad exclude here would silently drop
            // service-loader registrations or license files the next time a
            // dependency is added.
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
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
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("me.saket.telephoto:zoomable-image-coil:0.14.0")
    implementation("me.saket.swipe:swipe:1.3.0")
    // Through the BOM rather than a hard 4.12.0 pin, and the same BOM version
    // :source-api declares — keep the two in step.
    //
    // The old pin was a floor, not a ceiling: Gradle resolves version conflicts
    // to the highest, and :source-api's 5.0.0-alpha.12 already outranked
    // 4.12.0, so this module has in practice been running OkHttp 5 for a long
    // time while its build file claimed 4. That is worth knowing before reading
    // any of the network code against the 4.x docs.
    implementation(platform("com.squareup.okhttp3:okhttp-bom:5.4.0"))
    implementation("com.squareup.okhttp3:okhttp")
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
    // 1.9.0, not 1.7.3. Extensions built with a newer serialization plugin emit
    // @Serializable classes whose generated serializers do NOT override
    // `GeneratedSerializer.typeParametersSerializers()`, because from 1.8 the
    // runtime supplies a default body. On 1.7.3 that method is still abstract,
    // so the first time such a serializer is touched the call dies with
    // `AbstractMethodError: abstract method
    // "KSerializer[] GeneratedSerializer.typeParametersSerializers()"`.
    //
    // Asura Scans 1.6.66 and SpyFakku 1.4.16 both hit it: the source browses
    // nothing and reports that error. Anything using kotlinx.serialization for
    // its API responses is exposed, which is most JSON-backed sources.
    //
    // **1.9.0 specifically, not the newest.** Releases are pinned to a Kotlin
    // version — 1.9.0 is built on Kotlin 2.2.0 and this project is on 2.2.21,
    // so its metadata is readable. 1.10.0 is built on Kotlin 2.3.0 and a 2.2
    // compiler refuses 2.3 metadata outright, which is the same wall OkHttp
    // 5.2+ put in front of Kotlin 2.0.20 earlier tonight. Moving past 1.9.x
    // means moving Kotlin first.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}
