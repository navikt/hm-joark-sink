import com.expediagroup.graphql.plugin.gradle.config.GraphQLScalar
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.graphql)
    alias(libs.plugins.openapi)
    alias(libs.plugins.spotless)
}

application {
    applicationName = "hm-joark-sink"
    mainClass.set("no.nav.hjelpemidler.joark.ApplicationKt")
}

dependencies {
    implementation(platform(libs.hotlibs.platform))

    // hotlibs
    implementation(libs.hotlibs.core)
    implementation(libs.hotlibs.http)
    implementation(libs.hotlibs.serialization)

    implementation(libs.kotlin.logging)
    implementation(libs.rapidsAndRivers)

    // GraphQL
    implementation(libs.graphql.ktor.client) {
        exclude("com.expediagroup", "graphql-kotlin-client-serialization") // prefer jackson
        exclude("io.ktor", "ktor-client-serialization") // prefer ktor-client-jackson
    }
    implementation(libs.graphql.client.jackson)
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

@Suppress("UnstableApiUsage")
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useKotlinTest(libs.versions.kotlin.asProvider())
            dependencies {
                implementation(libs.hotlibs.test)
                implementation(libs.tbdLibs.rapidsAndRivers.test)
            }
        }
    }
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

val openApiGenerated: Provider<Directory> = layout.buildDirectory.dir("generated/source")
openApiGenerate {
    generatorName.set("kotlin")
    skipValidateSpec.set(true)
    inputSpec.set(layout.projectDirectory.file("src/main/resources/dokarkiv/openapi.yaml").toString())
    outputDir.set(openApiGenerated.map { it.dir("dokarkiv").toString() })
    packageName.set("no.nav.hjelpemidler.joark.dokarkiv")
    globalProperties.set(
        mapOf(
            "apis" to "none",
            "models" to "",
            "modelDocs" to "false",
            "modelTests" to "false",
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
    generatorName.set("kotlin")
    skipValidateSpec.set(true)
    inputSpec.set(layout.projectDirectory.file("src/main/resources/førstesidegenerator/openapi.yaml").toString())
    outputDir.set(openApiGenerated.map { it.dir("førstesidegenerator").toString() })
    packageName.set("no.nav.hjelpemidler.joark.førstesidegenerator")
    globalProperties.set(
        mapOf(
            "apis" to "none",
            "models" to "",
            "modelDocs" to "false",
            "modelTests" to "false",
        ),
    )
    configOptions.set(
        mapOf(
            "serializationLibrary" to "jackson",
            "dateLibrary" to "java8-localdatetime", // burde vært default ("java8"), men førstesidegenerator benytter LocalDateTime
            "enumPropertyNaming" to "UPPERCASE",
            "sourceFolder" to "main",
        ),
    )
}

sourceSets {
    main {
        kotlin {
            srcDir(openApiGenerated.map { it.dir("dokarkiv/main") })
            srcDir(openApiGenerated.map { it.dir("førstesidegenerator/main") })
        }
    }
}

tasks {
    compileKotlin { dependsOn(openApiGenerate, førstesidegenerator) }
    shadowJar { mergeServiceFiles() }
}
