plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.genjur.stream"
    compileSdk = 35

    val stableKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    val stableKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    val stableKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
    val stableKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
    val hasStableSigning = listOf(
        stableKeystorePath,
        stableKeystorePassword,
        stableKeyAlias,
        stableKeyPassword
    ).all { !it.isNullOrBlank() }

    signingConfigs {
        if (hasStableSigning) {
            create("stableRelease") {
                storeFile = file(stableKeystorePath!!)
                storePassword = stableKeystorePassword
                keyAlias = stableKeyAlias
                keyPassword = stableKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.genjur.stream"
        minSdk = 24
        targetSdk = 35
        versionCode = System.getenv("GENJUR_VERSION_CODE")?.toIntOrNull() ?: 8
        versionName = System.getenv("GENJUR_VERSION_NAME") ?: "0.5.3"
    }

    buildTypes {
        getByName("debug") {
            // Use the stable key when configured; otherwise use Android's generated debug key.
            if (hasStableSigning) {
                signingConfig = signingConfigs.getByName("stableRelease")
            }
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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jsoup:jsoup:1.18.3")

    // CloudStream 4.9.1 extension API used by .cs3 providers.
    implementation("com.github.recloudstream.cloudstream:library:v4.9.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    implementation("com.github.Blatzar:NiceHttp:0.4.11")
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.25.2")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-https:8.1.9")
    implementation("androidx.media3:media3-ui:1.5.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
