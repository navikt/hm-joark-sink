package no.nav.hjelpemidler.joark.service.hotsak

import com.fasterxml.jackson.annotation.JsonIgnore
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import no.nav.hjelpemidler.domain.enhet.Enhetsnummer
import no.nav.hjelpemidler.domain.joark.EndretDokument
import no.nav.hjelpemidler.domain.joark.JournalpostSak
import no.nav.hjelpemidler.domain.joark.isFagsaksystemHotsak
import no.nav.hjelpemidler.domain.kodeverk.Fagsaksystem
import no.nav.hjelpemidler.domain.person.Fødselsnummer
import no.nav.hjelpemidler.domain.tilgang.UtførtAvId
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.joark.service.hotsak.JournalpostJournalførtOppdaterOgFerdigstillJournalpost.IncomingMessage
import no.nav.hjelpemidler.kafka.KafkaEvent
import no.nav.hjelpemidler.kafka.KafkaMessage
import no.nav.hjelpemidler.rapids_and_rivers.ExtendedMessageContext
import no.nav.hjelpemidler.rapids_and_rivers.KafkaMessageListener
import java.time.LocalDateTime
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
        val journalpostId = message.journalpostId
        val oppgaveId = message.oppgaveId
        val oppgavegrunnlagId = message.oppgavegrunnlagId

        log.info { "Oppdaterer og ferdigstiller journalpost, $message" }

        val journalpostFerdigstilt = journalpostService.ferdigstillJournalpost(
            journalpostId = journalpostId,
            tittel = message.tittel,
            endredeDokumenter = message.endredeDokumenter,
            fnrBruker = message.fnrBruker,
            sak = message.sak ?: JournalpostSak.Fagsak(
                fagsakId = message.sakId ?: error("Mangler sakId for journalføring, $message"),
                fagsaksystem = Fagsaksystem.HJELPEMIDLER,
            ),
            journalførendeEnhet = message.journalførendeEnhet,
        )

        val sak = journalpostFerdigstilt.sak
        if (sak is JournalpostSak.GenerellSak || !sak.isFagsaksystemHotsak) {
            log.info { "Journalpost ferdigstilt og tilknyttet ekstern sak, $journalpostFerdigstilt" }
            return
        }

        log.info { "Journalpost ferdigstilt og tilknyttet Hotsak-sak, $journalpostFerdigstilt" }

        val fnrBruker = journalpostFerdigstilt.fnrBruker
        context.publish(
            key = fnrBruker.toString(),
            message = OutgoingMessage(
                journalpostId = journalpostFerdigstilt.journalpostId,
                nyJournalpostId = journalpostFerdigstilt.nyJournalpostId,
                hoveddokumentTittel = journalpostFerdigstilt.hoveddokumentTittel,
                fnrBruker = journalpostFerdigstilt.fnrBruker,
                sakId = sak.fagsakId,
                sak = sak,
                journalførendeEnhet = journalpostFerdigstilt.journalførendeEnhet,
                journalførtAv = message.journalførtAv,
                oppgaveId = oppgaveId,
                oppgavegrunnlagId = oppgavegrunnlagId,
            )
        )
    }

    @KafkaEvent(IncomingMessage.EVENT_NAME, alternativeNames = [IncomingMessage.ALTERNATIVE_EVENT_NAME])
    data class IncomingMessage(
        val journalpostId: String,
        @Deprecated("Byttes med dokumenter")
        val dokumentId: String?,
        @Deprecated("Byttes med dokumenter")
        val dokumenttittel: String?,
        /**
         * Tittel som beskriver journalposten samlet.
         */
        val tittel: String?,
        val dokumenter: List<EndretDokument>?,
        val fnrBruker: Fødselsnummer,
        @Deprecated("Byttes med sak")
        val sakId: String?,
        val sak: JournalpostSak?,
        val journalførendeEnhet: Enhetsnummer,
        val journalførtAv: UtførtAvId?,
        /**
         * Id for journalføringsoppgaven.
         */
        val oppgaveId: String?,
        val oppgavegrunnlagId: UUID?,
        override val eventId: UUID,
    ) : KafkaMessage {
        val endredeDokumenter
            @JsonIgnore
            get() = if (dokumentId == null || dokumenttittel == null) {
                dokumenter
            } else {
                listOf(EndretDokument(dokumentId, dokumenttittel))
            }

        override fun toString(): String = if (sak == null) {
            "journalpostId: $journalpostId, journalførendeEnhet: $journalførendeEnhet, oppgaveId: $oppgaveId, oppgavegrunnlagId: $oppgavegrunnlagId, sakId: $sakId"
        } else {
            "journalpostId: $journalpostId, journalførendeEnhet: $journalførendeEnhet, oppgaveId: $oppgaveId, oppgavegrunnlagId: $oppgavegrunnlagId, $sak"
        }

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
        val fnrBruker: Fødselsnummer,
        @Deprecated("Byttes med sak")
        val sakId: String,
        val sak: JournalpostSak,
        val journalførendeEnhet: Enhetsnummer,
        val journalførtAv: UtførtAvId?,
        /**
         * Id for journalføringsoppgaven.
         */
        val oppgaveId: String?,
        val oppgavegrunnlagId: UUID?,
        val opprettet: LocalDateTime = LocalDateTime.now(),
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
