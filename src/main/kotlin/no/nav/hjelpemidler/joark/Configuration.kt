package no.nav.hjelpemidler.joark

import no.nav.hjelpemidler.configuration.EnvironmentVariable

object Configuration {
    val EVENT_NAME by EnvironmentVariable

    // Joark
    val JOARK_BASE_URL by EnvironmentVariable
    val JOARK_SCOPE by EnvironmentVariable

    // Førstesidegenerator
    val FØRSTESIDEGENERATOR_BASE_URL by EnvironmentVariable
    val FØRSTESIDEGENERATOR_SCOPE by EnvironmentVariable

    // PDF-generator
    val PDF_BASE_URL by EnvironmentVariable

    // SAF
    val SAF_GRAPHQL_URL by EnvironmentVariable
    val SAF_REST_URL by EnvironmentVariable
    val SAF_SCOPE by EnvironmentVariable
}
