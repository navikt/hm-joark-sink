package no.nav.hjelpemidler.joark.service.barnebriller

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.JsonNode
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.kafka.KafkaEvent
import no.nav.hjelpemidler.kafka.KafkaMessage
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class JournalpostBarnebrillevedtakData(
    val fnr: String,
    val orgnr: String,
    val sakId: String,
    val brukersNavn: String,
    val orgNavn: String,
    val orgAdresse: String,
    val navnAvsender: String,
    val brilleseddel: JsonNode,
    val bestillingsdato: LocalDate,
    val bestillingsår: Int = bestillingsdato.year,
    val bestillingsreferanse: String,
    val satsBeskrivelse: String,
    val satsBeløp: BigDecimal,
    val beløp: BigDecimal,
    val dokumenttype: Dokumenttype = Dokumenttype.KRAV_BARNEBRILLER_OPTIKER,
    val dokumentTittel: String = dokumenttype.tittel,

    @JsonAlias("opprettetDato")
    val opprettet: LocalDateTime,
) {
    /**
     * Må ha egen klasse her fordi man serialiserer denne (JournalpostBarnebrillevedtakData) for to forskjellige formål
     * formål (pdf-generator, og event) med forskjellig innhold.
     */
    @KafkaEvent("hm-opprettetOgFerdigstiltBarnebrillerJournalpost")
    data class Utgående(
        val fnr: String,
        val orgnr: String,
        val sakId: String,
        val joarkRef: String,
        val dokumentIder: Set<String>,
        val dokumentTittel: String,
        val opprettet: LocalDateTime = LocalDateTime.now(),
        override val eventId: UUID = UUID.randomUUID(),
    ) : KafkaMessage

    fun tilUtgående(journalpostId: String, dokumentIder: Set<String>) = Utgående(
        fnr = fnr,
        orgnr = orgnr,
        sakId = sakId,
        joarkRef = journalpostId,
        dokumentIder = dokumentIder,
        dokumentTittel = dokumentTittel,
    )
}
