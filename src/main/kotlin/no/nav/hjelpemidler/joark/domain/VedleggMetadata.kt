package no.nav.hjelpemidler.joark.domain

import java.util.UUID

data class VedleggMetadata (
    val id: UUID,
    val type: VedleggType,
) {
    fun tilVedlegg(pdf: ByteArray) = Vedlegg(id, type, pdf)
}

enum class VedleggType {
    LEGEERKLÆRING
}

data class Vedlegg(
    val id: UUID,
    val type: VedleggType,
    val pdf: ByteArray,
)