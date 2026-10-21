package no.nav.hjelpemidler.joark.sink.service.hotsak

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import no.nav.hjelpemidler.domain.enhet.Enhetsnummer
import no.nav.hjelpemidler.domain.joark.EndretDokument
import no.nav.hjelpemidler.domain.joark.JournalpostSak
import no.nav.hjelpemidler.domain.joark.isFagsaksystemHotsak
import no.nav.hjelpemidler.domain.person.Fødselsnummer
import no.nav.hjelpemidler.domain.tilgang.UtførtAvId
import no.nav.hjelpemidler.joark.sink.service.JournalpostService
import no.nav.hjelpemidler.joark.sink.service.hotsak.JournalpostJournalførtOppdaterOgFerdigstillJournalpost.IncomingMessage
import no.nav.hjelpemidler.kafka.KafkaEvent
import no.nav.hjelpemidler.kafka.KafkaMessage
import no.nav.hjelpemidler.rapids_and_rivers.ExtendedMessageContext
import no.nav.hjelpemidler.rapids_and_rivers.KafkaMessageListener
import java.time.Instant
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Oppdater og ferdigstill journalpost etter manuell journalføring i Hotsak.
 */
class JournalpostJournalførtOppdaterOgFerdigstillJournalpost(
    private val journalpostService: JournalpostService,
) : KafkaMessageListener<IncomingMessage>(failOnError = true) {
    override fun skipMessage(
        message: JsonMessage,
        context: ExtendedMessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ): Boolean {
        val journalpostId = message["journalpostId"].stringValue()
        return journalpostId in skip
    }

    override suspend fun onMessage(
        message: IncomingMessage,
        context: ExtendedMessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        log.info { "Oppdaterer og ferdigstiller journalpost, $message" }

        val journalpostFerdigstilt = journalpostService.ferdigstillJournalpost(
            journalpostId = message.journalpostId,
            tittel = message.tittel,
            endredeDokumenter = message.dokumenter,
            brukerFnr = message.brukerFnr,
            avsenderFnr = message.avsenderFnr ?: message.brukerFnr,
            sak = message.sak,
            søknadId = message.søknadId,
            journalførendeEnhet = message.journalførendeEnhet,
        )

        val sak = journalpostFerdigstilt.sak
        if (sak is JournalpostSak.GenerellSak || !sak.isFagsaksystemHotsak) {
            log.info { "Journalpost ferdigstilt og tilknyttet ekstern sak, $journalpostFerdigstilt" }
            return
        }

        log.info { "Journalpost ferdigstilt og tilknyttet Hotsak-sak, $journalpostFerdigstilt" }

        context.publish(
            key = journalpostFerdigstilt.brukerFnr.toString(),
            message = OutgoingMessage(
                journalpostId = journalpostFerdigstilt.journalpostId,
                nyJournalpostId = journalpostFerdigstilt.nyJournalpostId,
                hoveddokumentTittel = journalpostFerdigstilt.hoveddokumentTittel,
                brukerFnr = journalpostFerdigstilt.brukerFnr,
                brukerNavn = message.brukerNavn,
                avsenderFnr = journalpostFerdigstilt.avsenderFnr,
                sakId = sak.fagsakId,
                sak = sak,
                journalførendeEnhet = journalpostFerdigstilt.journalførendeEnhet,
                journalførtAv = message.journalførtAv,
                oppgaveId = message.oppgaveId,
                oppgavegrunnlagId = message.oppgavegrunnlagId,
                søknadId = message.søknadId,
            )
        )
    }

    @KafkaEvent(IncomingMessage.EVENT_NAME, alternativeNames = [IncomingMessage.ALTERNATIVE_EVENT_NAME])
    data class IncomingMessage(
        val journalpostId: String,
        /**
         * Tittel som beskriver journalposten samlet.
         */
        val tittel: String?,
        val dokumenter: List<EndretDokument>?,
        @JsonAlias("fnrBruker")
        val brukerFnr: Fødselsnummer,
        @JsonAlias("navnBruker")
        val brukerNavn: String?,
        val avsenderFnr: Fødselsnummer?,
        val sak: JournalpostSak,
        val journalførendeEnhet: Enhetsnummer,
        val journalførtAv: UtførtAvId?,
        /**
         * Id for journalføringsoppgaven.
         */
        val oppgaveId: String?,
        val oppgavegrunnlagId: UUID?,
        /**
         * Id for søknad generert i Hotsak ved opprettelse av sak.
         */
        val søknadId: UUID?,
        override val eventId: UUID,
    ) : KafkaMessage {
        override fun toString(): String =
            "journalpostId: $journalpostId, journalførendeEnhet: $journalførendeEnhet, oppgaveId: $oppgaveId, oppgavegrunnlagId: $oppgavegrunnlagId, søknadId: $søknadId, $sak"

        companion object {
            const val EVENT_NAME = "hm-journalpost-journalført"
            const val ALTERNATIVE_EVENT_NAME = "hm-journalpost-journalført-ekstern-sak"
        }
    }

    @KafkaEvent(OutgoingMessage.EVENT_NAME)
    data class OutgoingMessage(
        val journalpostId: String,
        val nyJournalpostId: String,
        val hoveddokumentTittel: String?,
        @JsonProperty("fnrBruker")
        val brukerFnr: Fødselsnummer,
        @JsonProperty("navnBruker")
        val brukerNavn: String?,
        val avsenderFnr: Fødselsnummer,
        val sakId: String,
        val sak: JournalpostSak,
        val journalførendeEnhet: Enhetsnummer,
        val journalførtAv: UtførtAvId?,
        /**
         * Id for journalføringsoppgaven.
         */
        val oppgaveId: String?,
        val oppgavegrunnlagId: UUID?,
        val søknadId: UUID?,
        override val opprettet: Instant = Instant.now(),
        override val eventId: UUID = UUID.randomUUID(),
    ) : KafkaMessage {
        companion object {
            const val EVENT_NAME = "hm-journalpost-oppdatert-og-ferdigstilt"
        }
    }

    companion object {
        private val skip = setOf(
            "453827301",
            "598126522",
            "609522349",
            "610130874",
            "610767289",
            "611390815",
            "453837166",
            "453901864",
            "747535981",
            "748025006",
        )
    }
}
