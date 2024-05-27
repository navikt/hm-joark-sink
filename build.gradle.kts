import com.expediagroup.graphql.plugin.gradle.config.GraphQLScalar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.graphql)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.openapi)
    alias(libs.plugins.spotless)
}

application {
    applicationName = "hm-joark-sink"
    mainClass.set("no.nav.hjelpemidler.joark.ApplicationKt")
}

dependencies {
    implementation(enforcedPlatform(libs.ktor.bom))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.logging)
    implementation(libs.rapidsAndRivers)
    implementation(libs.hm.http)

    // GraphQL
    implementation(libs.graphql.ktor.client) {
        exclude("com.expediagroup", "graphql-kotlin-client-serialization") // prefer jackson
        exclude("io.ktor", "ktor-client-serialization") // prefer ktor-client-jackson
    }
    implementation(libs.graphql.client.jackson)

    // Testing
    testImplementation(libs.bundles.test)
}

kotlin { jvmToolchain(21) }

tasks.test {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.SHORT
        events = setOf(TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

tasks.compileKotlin {
    dependsOn(tasks.openApiGenerate, førstesidegenerator)
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
    inputSpec.set("$rootDir/src/main/resources/dokarkiv/openapi.yaml")
    outputDir.set("$buildDir/generated/source/dokarkiv")
    generatorName.set("kotlin")
    packageName.set("no.nav.hjelpemidler.joark.dokarkiv")
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

val førstesidegenerator by tasks.registering(GenerateTask::class) {
    skipValidateSpec.set(true)
    inputSpec.set("$rootDir/src/main/resources/førstesidegenerator/openapi.yaml")
    outputDir.set("$buildDir/generated/source/førstesidegenerator")
    generatorName.set("kotlin")
    packageName.set("no.nav.hjelpemidler.joark.førstesidegenerator")
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
            srcDir("$buildDir/generated/source/førstesidegenerator/main")
        }
    }
}
