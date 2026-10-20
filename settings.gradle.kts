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
        // ffmpeg-kit was retired from Maven Central (2025). These mirrors still
        // serve the cached AAR; the resolver falls through until one has it.
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://repo.huaweicloud.com/repository/maven/")
        maven("https://jitpack.io")
        maven("https://jcenter.bintray.com")
    }
}
rootProject.name = "BRAHM Auto-Editor"
include(":app")
