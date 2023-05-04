package no.nav.hjelpemidler.joark

import no.nav.hjelpemidler.configuration.EnvironmentVariable

object Configuration {
    val EVENT_NAME by EnvironmentVariable

    // Joark
    val JOARK_BASEURL by EnvironmentVariable
    val JOARK_SCOPE by EnvironmentVariable

    // PDF-generator
    val PDF_BASEURL by EnvironmentVariable

    // SAF
    val SAF_GRAPHQL_URL by EnvironmentVariable
    val SAF_REST_URL by EnvironmentVariable
    val SAF_SCOPE by EnvironmentVariable
}
