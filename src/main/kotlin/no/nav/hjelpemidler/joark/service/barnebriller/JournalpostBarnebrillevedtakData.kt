package no.nav.hjelpemidler.joark.service.barnebriller

import com.fasterxml.jackson.databind.JsonNode
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.jsonMessage
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class JournalpostBarnebrillevedtakData(
    val fnr: String,
    val brukersNavn: String,
    val orgnr: String,
    val orgNavn: String,
    val orgAdresse: String,
    val sakId: String,
    val navnAvsender: String,
    val brilleseddel: JsonNode,
    val opprettet: LocalDateTime,
    val bestillingsdato: LocalDate,
    val bestillingsår: Int,
    val bestillingsreferanse: String,
    val satsBeskrivelse: String,
    val satsBeløp: BigDecimal,
    val beløp: BigDecimal,
    val dokumenttype: Dokumenttype = Dokumenttype.VEDTAKSBREV_BARNEBRILLER_OPTIKER,
) {
    @Deprecated("Bruk Jackson direkte")
    fun toJson(journalpostId: String, dokumentIder: List<String>, eventName: String): String {
        return jsonMessage {
            it["fnr"] = this.fnr
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["orgnr"] = this.orgnr
            it["joarkRef"] = journalpostId
            it["dokumentIder"] = dokumentIder
            it["sakId"] = this.sakId
            it["dokumentTittel"] = this.dokumenttype.tittel
            it["eventId"] = UUID.randomUUID()
        }.toJson()
    }
}
