pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // FFmpegKit was retired from Maven Central in 2025. If the dependency
        // fails to resolve, add a mirror here, e.g.:
        // maven("https://raw.githubusercontent.com/jitpack/maven-mirror/master")
        maven("https://jitpack.io")
    }
}
rootProject.name = "BRAHM Auto-Editor"
include(":app")
