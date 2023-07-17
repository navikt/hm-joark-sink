import com.expediagroup.graphql.plugin.gradle.config.GraphQLScalar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.graphql)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.ktor)
    alias(libs.plugins.openapi)
    alias(libs.plugins.spotless)
}

application {
    applicationName = "hm-joark-sink"
    mainClass.set("no.nav.hjelpemidler.joark.ApplicationKt")
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.logging)
    implementation(enforcedPlatform(libs.ktor.bom))
    implementation(libs.rapidsAndRivers)

    // HTTP
    implementation(libs.hm.http)

    // GraphQL
    implementation(libs.graphql.kotlin.ktor.client) {
        exclude("com.expediagroup", "graphql-kotlin-client-serialization") // prefer jackson
        exclude("io.ktor", "ktor-client-serialization") // prefer ktor-client-jackson
    }
    implementation(libs.graphql.kotlin.client.jackson)

    // Testing
    testImplementation(libs.bundles.test)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.SHORT
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
