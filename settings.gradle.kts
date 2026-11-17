pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Use Google's Maven Central mirror as the sole Central endpoint.
        // Falling back to repo.maven.apache.org caused HTTP 429 during Android tests.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "SharafzaiKeyboard"
include(":app")
