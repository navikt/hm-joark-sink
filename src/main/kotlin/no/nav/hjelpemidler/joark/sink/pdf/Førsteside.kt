package no.nav.hjelpemidler.joark.sink.pdf

data class Førsteside(
    val fysiskDokument: ByteArray,
    val tittel: String,
    val brevkode: String? = null,
)
