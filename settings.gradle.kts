pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Mapbox repository
        maven { url = uri("https://api.mapbox.com/downloads/v2/releases/maven") }

        // JitPack repository
        maven { url = uri("https://jitpack.io") }
        jcenter()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Mapbox repository
        maven { url = uri("https://api.mapbox.com/downloads/v2/releases/maven") }

        // JitPack repository
        maven { url = uri("https://jitpack.io") }
        jcenter()
    }
}

rootProject.name = "iSpeed"
include(":app")
 