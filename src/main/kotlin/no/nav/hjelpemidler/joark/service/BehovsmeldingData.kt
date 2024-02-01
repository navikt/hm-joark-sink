package no.nav.hjelpemidler.joark.service

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.hjelpemidler.joark.domain.Sakstype
import java.time.LocalDateTime
import java.util.UUID

data class BehovsmeldingData(
    val fnrBruker: String,
    val navnBruker: String,
    val behovsmeldingId: UUID,
    val behovsmeldingJson: String,
    val behovsmeldingGjelder: String,
    val sakstype: Sakstype
) {
    @Deprecated("Bruk Jackson direkte")
    fun toJson(journalpostId: String, eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["soknadId"] = this.behovsmeldingId
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["fodselNrBruker"] = this.fnrBruker // @deprecated
            it["fnrBruker"] = this.fnrBruker
            it["joarkRef"] = journalpostId
            it["eventId"] = UUID.randomUUID()
            it["soknadGjelder"] = behovsmeldingGjelder
            it["sakstype"] = sakstype.name
        }.toJson()
    }
}
