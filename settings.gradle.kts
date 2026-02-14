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
        maven { url = uri("https://chaquo.com/maven") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://chaquo.com/maven") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MarketMonitor_rev2"
include(":app")
includeBuild("D:/android_2025/kotlin_krx") {
    dependencySubstitution {
        substitute(module("com.krxkt:kotlin-krx")).using(project(":"))
    }
}