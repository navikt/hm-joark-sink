package no.nav.hjelpemidler.joark.sink.service

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.hjelpemidler.joark.sink.Configuration
import no.nav.hjelpemidler.joark.sink.domain.Dokumenttype
import no.nav.hjelpemidler.joark.sink.domain.Sakstype
import no.nav.hjelpemidler.joark.sink.domain.VedleggMetadata
import no.nav.hjelpemidler.kafka.KafkaMessage
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

    @JsonProperty("vedlegg")
    val vedleggMetadata: List<VedleggMetadata>,
) : KafkaMessage {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    override val eventId: UUID = UUID.randomUUID()

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    override val eventName: String = Configuration.EVENT_NAME

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    val opprettet: LocalDateTime = LocalDateTime.now()

    @Deprecated("Bruk fnrBruker")
    val fodselNrBruker by this::fnrBruker
}
