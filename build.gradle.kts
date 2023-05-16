import com.expediagroup.graphql.plugin.gradle.config.GraphQLScalar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    application
    kotlin("jvm") version "1.8.20"
    id("com.diffplug.spotless") version "6.12.1"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.expediagroup.graphql") version "6.4.0"
    id("org.openapi.generator") version "6.5.0"
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
    implementation(platform("io.ktor:ktor-bom:2.3.0"))
    implementation("com.github.navikt:rapids-and-rivers:2023042611061682500003.f24c0756e00a")

    // GraphQL
    val graphQLVersion = "6.4.0"
    implementation("com.expediagroup:graphql-kotlin-ktor-client:$graphQLVersion") {
        exclude("com.expediagroup", "graphql-kotlin-client-serialization") // prefer jackson
        exclude("io.ktor", "ktor-client-serialization") // prefer ktor-client-jackson
    }
    implementation("com.expediagroup:graphql-kotlin-client-jackson:$graphQLVersion")

    // Logging
    implementation("io.github.microutils:kotlin-logging:3.0.5")

    // Http
    implementation("no.nav.hjelpemidler.http:hm-http:v0.0.35")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.4")
    testImplementation("io.kotest:kotest-runner-junit5:5.5.5")
    testImplementation("io.kotest:kotest-assertions-core:5.5.5")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

tasks.compileKotlin {
    kotlinOptions.jvmTarget = "17"
    dependsOn(tasks.openApiGenerate)
}

graphql {
    client {
        schemaFile = file("src/main/resources/saf/saf-api-sdl.graphqls")
        queryFileDirectory = "src/main/resources/saf"
        packageName = "no.nav.hjelpemidler.saf"
        customScalars = listOf(
            GraphQLScalar("Date", "java.time.LocalDate", "no.nav.hjelpemidler.saf.DateScalarConverter"),
            GraphQLScalar("DateTime", "java.time.LocalDateTime", "no.nav.hjelpemidler.saf.DateTimeScalarConverter"),
        )
        allowDeprecatedFields = true
    }
}

openApiGenerate {
    skipValidateSpec.set(true)
    inputSpec.set("src/main/resources/dokarkiv/openapi.yaml")
    outputDir.set("$buildDir/generated/source/dokarkiv")
    generatorName.set("kotlin")
    packageName.set("no.nav.hjelpemidler.dokarkiv")
    globalProperties.set(
        mapOf(
            "apis" to "none",
            "models" to "",
            "modelDocs" to "false",
        ),
    )
    configOptions.set(
        mapOf(
            "serializationLibrary" to "jackson",
            "dateLibrary" to "java8-localdatetime", // burde vært default ("java8"), men eksterne saf/dokarkiv benytter LocalDateTime
            "enumPropertyNaming" to "UPPERCASE",
            "sourceFolder" to "main",
        ),
    )
}

sourceSets {
    main {
        kotlin {
            srcDir("$buildDir/generated/source/dokarkiv/main")
        }
    }
}
