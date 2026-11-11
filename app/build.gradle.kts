plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sharafzai.keyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sharafzai.keyboard"
        minSdk = 21
        targetSdk = 34
        versionCode = 5
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("ANDROID_KEYSTORE_FILE") ?: "${System.getProperty("user.home")}/.android/sharafzai-release.jks")
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = "sharafzai"
            keyPassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            // Optimized, directly installable APK for distribution.
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    // MVP intentionally uses Android's built-in PdfRenderer; no heavy OCR/PDF engine.
}
