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

rootProject.name = "EasyTierMD3"

include(":app")
include(":core:easytier-api")
include(":core:easytier-bridge")
include(":core:native")
include(":domain")
include(":data")
include(":service")
include(":feature:home")
include(":feature:network")
include(":feature:peer")
include(":feature:logs")
include(":feature:settings")
include(":ui:theme")
include(":ui:component")
include(":ui:navigation")
