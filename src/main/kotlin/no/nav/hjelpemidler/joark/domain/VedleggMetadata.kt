package no.nav.hjelpemidler.joark.domain

import java.util.UUID

data class VedleggMetadata (
    val id: UUID,
    val type: VedleggType,
    val navn: String,
) {
    fun tilVedlegg(pdf: ByteArray) = Vedlegg(id, type, navn, pdf)
}

enum class VedleggType {
    LEGEERKLÆRING_FOR_VARMEHJELPEMIDDEL
}

data class Vedlegg(
    val id: UUID,
    val type: VedleggType,
    val navn: String,
    val pdf: ByteArray,
)