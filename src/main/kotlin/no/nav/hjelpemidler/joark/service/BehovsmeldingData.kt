package no.nav.hjelpemidler.joark.service

import no.nav.hjelpemidler.joark.domain.Sakstype
import no.nav.hjelpemidler.joark.jsonMessage
import java.time.LocalDateTime
import java.util.UUID

data class BehovsmeldingData(
    val fnrBruker: String,
    val behovsmeldingId: UUID,
    val behovsmeldingJson: String,
    val behovsmeldingGjelder: String,
    val sakstype: Sakstype,
    val erHast: Boolean,
) {
    @Deprecated("Bruk Jackson direkte")
    fun toJson(journalpostId: String, eventName: String): String {
        return jsonMessage {
            it["soknadId"] = this.behovsmeldingId
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["fodselNrBruker"] = this.fnrBruker // @deprecated
            it["fnrBruker"] = this.fnrBruker
            it["joarkRef"] = journalpostId
            it["eventId"] = UUID.randomUUID()
            it["soknadGjelder"] = behovsmeldingGjelder
            it["sakstype"] = sakstype.name
            it["erHast"] = erHast
        }.toJson()
    }
}
