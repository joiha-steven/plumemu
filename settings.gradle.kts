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

rootProject.name = "plumemu"

include(
    ":app",
    ":core-ui",
    ":core-input",
    ":core-data",
    ":library",
    ":identify",
    ":platforms",
    ":launcher",
    ":scraper",
    ":telemetry",
    // Test-only: Macrobenchmark and Baseline Profile generation against :app; never shipped.
    ":benchmark",
)
