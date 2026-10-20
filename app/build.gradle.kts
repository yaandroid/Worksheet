plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.brahm.autoeditor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.brahm.autoeditor"
        minSdk = 26
        targetSdk = 34
        versionCode = 1           // kept fixed pre-release
        versionName = "1.1"       // visible version, do not bump unless asked
        vectorDrawables { useSupportLibrary = true }
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Video processing on-device (same FFmpeg filtergraphs as the web engine).
    // NOTE: ffmpeg-kit was retired from Maven Central (2025). If this fails to
    // resolve, use a community mirror/AAR. See android/README.md.
    implementation("com.antonkarpenko:ffmpeg-kit-full-gpl:2.2.3")

    // HTTP for Groq Whisper transcription
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // On-device background removal (optional)
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")
}
