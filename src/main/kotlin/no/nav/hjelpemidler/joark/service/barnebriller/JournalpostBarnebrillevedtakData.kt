package no.nav.hjelpemidler.joark.service.barnebriller

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.hjelpemidler.joark.service.Dokumenttype
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
    val bestillingsreferanse: String,
    val satsBeskrivelse: String,
    val satsBeløp: BigDecimal,
    val beløp: BigDecimal,
    val dokumenttype: Dokumenttype = Dokumenttype.VEDTAKSBREV_BARNEBRILLER_OPTIKER,
) {
    @Deprecated("Bruk Jackson direkte")
    fun toJson(journalpostId: String, eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["fnr"] = this.fnr
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["orgnr"] = this.orgnr
            it["joarkRef"] = journalpostId
            it["sakId"] = this.sakId
            it["dokumentTittel"] = this.dokumenttype.tittel
            it["eventId"] = UUID.randomUUID()
        }.toJson()
    }
}
