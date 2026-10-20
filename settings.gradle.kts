@file:Suppress("UnstableApiUsage")

import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}



dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        ivy {
            name = "Node.js"
            setUrl("https://nodejs.org/dist/")
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("org.nodejs", "node")
            }
        }
    }
    versionCatalogs {
        create("ktorlibs") {
            from("io.ktor:ktor-version-catalog:3.5.0")
        }
    }
}

rootProject.name = "BallByBall"

listOf(
    "bbb-get-cricsheet-data",
    "bbb-update-database",
    "bbb-shared",
    "bbb-cli-shared",
    "bbb-parse-cricsheet",
    "bbb-bulkupdate",
    "bbb-api",
    "bbb-web",
).forEach { projectName ->
    if (rootDir.resolve(projectName).isDirectory) {
        include(projectName)
    }
}

