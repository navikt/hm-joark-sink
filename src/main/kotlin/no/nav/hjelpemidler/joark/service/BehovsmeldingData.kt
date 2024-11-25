package no.nav.hjelpemidler.joark.service

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.hjelpemidler.joark.Configuration
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.domain.Sakstype
import java.time.LocalDateTime
import java.util.UUID

data class BehovsmeldingData(
    val erHast: Boolean,
    val joarkRef: String? = null,

    @JsonAlias("behovsmeldingType")
    val sakstype: Sakstype,

    @JsonAlias("fodselNrBruker")
    val fnrBruker: String,

    @JsonProperty("soknadId")
    val behovsmeldingId: UUID,

    @JsonProperty("soknadGjelder")
    val behovsmeldingGjelder: String? = Dokumenttype.SØKNAD_OM_HJELPEMIDLER.tittel,
) {
    // @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    val eventId = UUID.randomUUID()

    // @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    val eventName = Configuration.EVENT_NAME

    // @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    val opprettet = LocalDateTime.now()

    val fodselNrBruker = fnrBruker // @deprecated
}
