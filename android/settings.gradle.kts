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

rootProject.name = "Sakhi"

include(":app")

// Core modules
include(":core:domain")
include(":core:data")
include(":core:inference")
include(":core:rag")
include(":core:network")
include(":core:ui")

// Feature modules
include(":feature:auth")
include(":feature:home")
include(":feature:checkup")
include(":feature:chat")

// Map nested module directories
project(":core:domain").projectDir = file("core/domain")
project(":core:data").projectDir = file("core/data")
project(":core:inference").projectDir = file("core/inference")
project(":core:rag").projectDir = file("core/rag")
project(":core:network").projectDir = file("core/network")
project(":core:ui").projectDir = file("core/ui")
project(":feature:auth").projectDir = file("feature/auth")
project(":feature:home").projectDir = file("feature/home")
project(":feature:checkup").projectDir = file("feature/checkup")
project(":feature:chat").projectDir = file("feature/chat")
