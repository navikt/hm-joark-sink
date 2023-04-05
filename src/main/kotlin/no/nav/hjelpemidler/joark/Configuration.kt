package no.nav.hjelpemidler.joark

import no.nav.hjelpemidler.configuration.EnvironmentVariable

object Configuration {
    val EVENT_NAME by EnvironmentVariable

    // Joark
    val JOARK_BASEURL by EnvironmentVariable
    val JOARK_SCOPE by EnvironmentVariable

    // Joark via digihot-proxy
    val JOARK_PROXY_BASEURL by EnvironmentVariable
    val JOARK_PROXY_SCOPE by EnvironmentVariable

    // PDF-generator
    val PDF_BASEURL by EnvironmentVariable
}
