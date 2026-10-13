import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.knowledgespike"
version = "1.0"

dependencies {
    implementation(project(":bbb-cli-shared"))
    implementation(project(":bbb-shared"))
    implementation(libs.commons.cli)
    implementation(libs.kotlinx.serialization)
    implementation(libs.logback.classic)
    implementation(libs.logback.core)
    implementation(libs.mariadb)
    implementation(libs.arrow.core)
    implementation(libs.dotenv.kotlin)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.junit.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.strikt)
    testImplementation(libs.sqlite)
    testImplementation(libs.testcontainers.mariadb)
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
    mainClass.set("com.knowledgespike.ballbyball.parsecricsheet.Application")
}