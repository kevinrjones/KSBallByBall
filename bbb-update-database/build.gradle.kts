import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.flyway)
    application
}

buildscript {
    dependencies {
        classpath(libs.flyway)
        classpath(libs.flyway.postgres)
    }
}

group = "com.knowledgespike"
version = "1.0"

dependencies {
    testImplementation(kotlin("test"))

    implementation(libs.logback.classic)
    implementation(libs.logback.core)
    implementation(libs.angus.mail)
    implementation(libs.angus.activation)

    testImplementation(libs.junit)
    testImplementation(libs.junit.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kluent)
    testImplementation(libs.strikt)
    testImplementation(libs.testcontainers.mariadb)
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.mysql.connector)

    implementation(libs.commons.cli)
    implementation(libs.kotlinx.serialization)
    implementation(libs.mariadb)
    implementation(libs.postgres)
    implementation(libs.sqlite)
    implementation(project(":bbb-shared"))
    implementation(project(":bbb-cli-shared"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.knowledgespike.ballbyball.parse.Application")

    applicationDefaultJvmArgs = listOf("-Dlogback.configurationFile=./logging/logback.xml")
}


val flywayDatabase = providers.gradleProperty("migration.database")
    .orElse(providers.environmentVariable("FLYWAY_DATABASE"))
    .orElse("mysql")
    .get()

flyway {
    url = providers.gradleProperty("flyway.url")
        .orElse(providers.environmentVariable("FLYWAY_URL"))
        .orElse("jdbc:mysql://localhost:3306/acs_ball_by_ball")
        .get()
    user = providers.gradleProperty("flyway.user")
        .orElse(providers.environmentVariable("FLYWAY_USER"))
        .orElse("ballbyball")
        .get()
    password = providers.gradleProperty("flyway.password")
        .orElse(providers.environmentVariable("FLYWAY_PASSWORD"))
        .orElse("")
        .get()
    schemas = if (flywayDatabase == "sqlite") arrayOf("main") else arrayOf("acs_ball_by_ball")
    locations = arrayOf("filesystem:${projectDir}/migrations/$flywayDatabase")
    sqlMigrationPrefix = ""
    mixed = flywayDatabase == "sqlite"
    baselineOnMigrate = true
    outOfOrder = true
}

