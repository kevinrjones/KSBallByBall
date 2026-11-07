import org.gradle.api.tasks.testing.Test

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.knowledgespike"
version = "1.0"

dependencies {
    implementation(libs.arrow.core)
    implementation(libs.kotlinx.serialization)
    implementation(libs.hikari.cp)
    implementation(libs.kotlin.reflect)
    implementation(libs.logback.classic)
    implementation(libs.logback.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.junit.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.strikt)
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