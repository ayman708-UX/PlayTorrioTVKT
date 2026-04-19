plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.playtorrio.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.playtorrio.tv"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        // GitHub repo used by the in-app updater (UpdateChecker reads BuildConfig.UPDATE_REPO)
        buildConfigField("String", "UPDATE_REPO", "\"ayman708-UX/PlayTorrioTVKT\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release.jks")
            storePassword = "playtorrio123"
            keyAlias = "playtorrio"
            keyPassword = "playtorrio123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    packaging {
        jniLibs {
            pickFirsts += listOf("**/libc++_shared.so")
            useLegacyPackaging = true
        }
    }
}

// Exclude stock media3-exoplayer and media3-ui — replaced by NuvioTV's forked AARs
configurations.all {
    exclude(group = "androidx.media3", module = "media3-exoplayer")
    exclude(group = "androidx.media3", module = "media3-ui")
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)

    // Compose core
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")

    // TV-specific Compose
    implementation("androidx.tv:tv-material:1.0.1")
    implementation("androidx.tv:tv-foundation:1.0.0-alpha11")

    // Android core
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // ExoPlayer / Media3 — forked core + UI + FFmpeg decoder from NuvioTV (built against 1.10.0-rc01)
    implementation(files("libs/lib-exoplayer-release.aar"))
    implementation(files("libs/lib-ui-release.aar"))
    implementation(files("libs/lib-decoder-ffmpeg-release.aar"))
    implementation("androidx.media3:media3-exoplayer-hls:1.10.0-rc01")
    implementation("androidx.media3:media3-common:1.10.0-rc01")
    implementation("androidx.media3:media3-datasource:1.10.0-rc01")
    implementation("androidx.media3:media3-decoder:1.10.0-rc01")
    implementation("androidx.media3:media3-extractor:1.10.0-rc01")
    implementation("androidx.media3:media3-container:1.10.0-rc01")

    // Required by forked media3-ui AAR
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // QR Code phone-remote
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.zxing:core:3.5.3")

    // HTML parsing (webstreamr scrapers)
    implementation("org.jsoup:jsoup:1.18.1")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.9")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
