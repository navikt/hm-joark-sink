import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    application
    kotlin("jvm") version Kotlin.version
    id(Spotless.spotless) version Spotless.version
    id(Shadow.shadow) version Shadow.version
}

buildscript {
    repositories {
        jcenter()
    }
}

apply {
    plugin(Spotless.spotless)
}

repositories {
    mavenCentral()
    jcenter()
    maven("https://packages.confluent.io/maven/")
    maven("https://jitpack.io")
}

application {
    applicationName = "hm-joark-sink"
    mainClassName = "no.nav.hjelpemidler.joark.ApplicationKt"
}

fun ktor(name: String) = "io.ktor:ktor-$name:1.6.7"

dependencies {
    implementation(Jackson.core)
    implementation(Jackson.kotlin)
    implementation(Jackson.jsr310)
    implementation("com.github.guepardoapps:kulid:1.1.2.0")
    implementation("com.github.navikt:rapids-and-rivers:2022.04.05-09.40.11a466d7ac70")
    implementation("org.slf4j:slf4j-api:2.0.0-alpha6") // fordi rapids-and-rivers er på logback-classic:1.3.0-alpha10 som krever slf4j >= 2.0.0-alpha4
    implementation("io.github.microutils:kotlin-logging:2.1.21")
    implementation(Ktor.serverNetty)
    implementation(Database.Kotlinquery)
    implementation(Fuel.fuel)
    implementation(Fuel.library("coroutines"))
    implementation(Konfig.konfig)
    implementation(kotlin("stdlib-jdk8"))
    implementation(ktor("client-core"))
    implementation(ktor("client-cio"))
    implementation(ktor("client-jackson"))

    testImplementation(kotlin("test"))
    testImplementation(KoTest.assertions)
    testImplementation(KoTest.runner)
    testImplementation(Ktor.ktorTest)
    testImplementation(Mockk.mockk)
    implementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
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

tasks.named("shadowJar") {
    dependsOn("test")
}

tasks.named("jar") {
    dependsOn("test")
}

tasks.named("compileKotlin") {
    dependsOn("spotlessCheck")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}
