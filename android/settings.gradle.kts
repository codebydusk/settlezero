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

rootProject.name = "settlezero"

include(":app")
include(":core:model")
include(":core:common")
include(":core:domain")
include(":core:database")
include(":core:data")
include(":core:sync")
include(":core:ui")
include(":feature:group")
include(":feature:expense")
include(":feature:settlement")
include(":feature:claim")

