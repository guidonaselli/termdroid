pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Termdroid"

include(":app")
include(":core")
include(":agent")
include(":exec")
include(":probe")
include(":terminal")
include(":rootfs")
include(":adb")
include(":tools-unix")
include(":tools-android")
include(":spike")
