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
        maven("https://repo.opencollab.dev/main/") { name = "opencollab" }
        maven("https://repo.opencollab.dev/maven-snapshots/") { name = "opencollab-snapshots" }
        maven("https://maven.lenni0451.net/releases") { name = "lenni0451" }
    }
}
rootProject.name = "SevenVoice"
include(":app")
