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

    buildTypes {
        release {
            // Optimized, directly installable APK for distribution.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
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
