package no.nav.hjelpemidler.joark.domain

import java.util.UUID

data class VedleggMetadata(
    val id: UUID,
    val type: VedleggType,
    val navn: String,
) {
    fun tilVedlegg(pdf: ByteArray) = Vedlegg(id, type, navn, pdf)
}

enum class VedleggType {
    LEGEERKLÆRING_FOR_VARMEHJELPEMIDDEL,
    DØRAUTOMATIKK_DØR_BILDE,
    DØRAUTOMATIKK_DØR_PRODUSENT_DOKUMENTASJON,
    DØRAUTOMATIKK_EKSISTERENDE_DØRAUTOMATIKK_BILDE,
    DØRAUTOMATIKK_MÅLSATT_TEGNING,
    DØRAUTOMATIKK_GODKJENNING_MONTERING,
}

data class Vedlegg(
    val id: UUID,
    val type: VedleggType,
    val navn: String,
    val pdf: ByteArray,
)