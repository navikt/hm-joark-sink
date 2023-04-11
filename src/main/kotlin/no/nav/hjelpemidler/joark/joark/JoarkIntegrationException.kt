package no.nav.hjelpemidler.joark.joark

class JoarkIntegrationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

fun joarkIntegrationException(message: String, cause: Throwable? = null): Nothing =
    throw JoarkIntegrationException(message, cause)
