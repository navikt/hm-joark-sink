package no.nav.hjelpemidler.joark.sink.domain

import java.util.UUID

data class VedleggMetadata(
    val id: UUID,
    val type: String,
    val navn: String,
) {
    fun tilVedlegg(pdf: ByteArray) = Vedlegg(id, type, navn, pdf)
}

data class Vedlegg(
    val id: UUID,
    val type: String,
    val navn: String,
    val pdf: ByteArray,
)