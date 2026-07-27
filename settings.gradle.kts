pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Required for com.github.mihonapp:injekt — it is not on mavenCentral.
        maven(url = "https://www.jitpack.io")
    }
}
rootProject.name = "MangaReader"
include(":app")
include(":source-api")
