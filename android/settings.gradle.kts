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
        // GeckoView (motor de Firefox) se publica en el Maven de Mozilla
        maven("https://maven.mozilla.org/maven2/") {
            content { includeGroupByRegex("org\\.mozilla\\..*") }
        }
    }
}

rootProject.name = "Kaze"
include(":app")
