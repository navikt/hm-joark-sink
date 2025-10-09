package no.nav.hjelpemidler.joark.domain

import java.util.UUID

data class Vedlegg (
    val id: UUID,
    val type: VedleggType,
)

enum class VedleggType {
    LEGEERKLÆRING
}