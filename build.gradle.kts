import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.8.20"
    id("com.diffplug.spotless") version "6.12.1"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

application {
    applicationName = "hm-joark-sink"
    mainClass.set("no.nav.hjelpemidler.joark.ApplicationKt")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(platform("io.ktor:ktor-bom:2.2.4"))
    implementation("com.github.navikt:rapids-and-rivers:2023031511211678875716.e6e2c9250860")

    // Logging
    implementation("io.github.microutils:kotlin-logging:3.0.5")

    // Http
    implementation("no.nav.hjelpemidler.http:hm-http:v0.0.26")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.4")
    testImplementation("io.kotest:kotest-runner-junit5:5.5.5")
    testImplementation("io.kotest:kotest-assertions-core:5.5.5")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}
