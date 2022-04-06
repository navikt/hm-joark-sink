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
    implementation("com.github.navikt:rapids-and-rivers:20210428115805-514c80c")
    implementation(Ktor.serverNetty)
    implementation(Database.Kotlinquery)
    implementation(Fuel.fuel)
    implementation(Fuel.library("coroutines"))
    implementation(Konfig.konfig)
    implementation(Kotlin.Logging.kotlinLogging)
    implementation(kotlin("stdlib-jdk8"))
    implementation(ktor("client-core"))
    implementation(ktor("client-cio"))
    implementation(ktor("client-jackson"))

    testImplementation(KoTest.assertions)
    testImplementation(KoTest.runner)
    testImplementation(Ktor.ktorTest)
    testImplementation(Mockk.mockk)
    implementation("com.github.tomakehurst:wiremock-jre8-standalone:2.32.0")
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
