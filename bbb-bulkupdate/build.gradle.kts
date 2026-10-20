import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    application
}

group = "com.knowledgespike"
version = "1.0"

dependencies {
    implementation(project(":bbb-shared"))
    implementation(project(":bbb-cli-shared"))
    implementation(libs.commons.cli)
    implementation(libs.logback.classic)
    implementation(libs.logback.core)
    implementation(libs.mariadb)
    implementation(libs.mysql.connector)
    implementation(libs.postgres)
    implementation(libs.sqlite)
    implementation(libs.arrow.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.junit.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.strikt)
    testImplementation(libs.kluent)
    testImplementation(libs.testcontainers.mariadb)
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.testcontainers.postgresql)
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
    mainClass.set("com.knowledgespike.ballbyball.bulkupdate.Application")
}
