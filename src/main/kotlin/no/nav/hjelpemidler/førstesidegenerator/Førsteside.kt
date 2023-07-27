package no.nav.hjelpemidler.førstesidegenerator

data class Førsteside(
    val fysiskDokument: ByteArray,
    val tittel: String,
    val brevkode: String? = null,
)
