plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sharafzai.keyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sharafzai.keyboard"
        minSdk = 23
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
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
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
}

tasks.register("downloadOcrTrainedData") {
    val outputDir = layout.projectDirectory.dir("src/main/assets/tessdata").asFile
    outputs.dir(outputDir)
    doLast {
        outputDir.mkdirs()
        listOf("ara", "fas", "eng").forEach { language ->
            val target = File(outputDir, "$language.traineddata")
            if (!target.exists() || target.length() < 100_000) {
                val url = java.net.URL("https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/$language.traineddata")
                url.openStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            }
        }
    }
}

tasks.named("preBuild").configure { dependsOn("downloadOcrTrainedData") }
