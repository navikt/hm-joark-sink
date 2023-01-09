import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.8.0"
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

fun ktor(name: String) = "io.ktor:ktor-$name:2.2.2"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.natpryce:konfig:1.6.10.0")
    implementation("com.github.navikt:rapids-and-rivers:2022122313141671797650.f806f770805a")

    // Logging
    implementation("io.github.microutils:kotlin-logging:3.0.4")
    runtimeOnly("ch.qos.logback:logback-classic:1.4.5")

    // fixme -> fjern og bruk ktor-client
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-coroutines:2.3.1")

    // fixme -> fjern og bruk MockEngine
    implementation("com.github.tomakehurst:wiremock:2.27.2")

    // Ktor
    implementation(ktor("serialization-jackson"))
    implementation(ktor("client-core"))
    implementation(ktor("client-cio"))
    implementation(ktor("client-jackson"))
    implementation(ktor("client-content-negotiation"))

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.3")
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
