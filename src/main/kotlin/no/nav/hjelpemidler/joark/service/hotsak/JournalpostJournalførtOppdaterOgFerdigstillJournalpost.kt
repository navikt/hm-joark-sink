package no.nav.hjelpemidler.joark.service.hotsak

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import no.nav.hjelpemidler.domain.person.Fødselsnummer
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
        val sakId = message.sakId
        log.info { "Oppdaterer og ferdigstiller journalpost, journalpostId: $journalpostId, sakId: $sakId, oppgaveId: $oppgaveId" }

        val fnrBruker = message.fnrBruker.toString()
        val nyJournalpostId = journalpostService.ferdigstillJournalpost(
            journalpostId = journalpostId,
            journalførendeEnhet = message.journalførendeEnhet,
            fnrBruker = fnrBruker,
            sakId = sakId,
            dokumentId = message.dokumentId,
            dokumenttittel = message.dokumenttittel,
        )

        context.publish(
            key = fnrBruker,
            message = OutgoingMessage(
                journalpostId = journalpostId,
                journalførendeEnhet = message.journalførendeEnhet,
                nyJournalpostId = nyJournalpostId,
                fnrBruker = fnrBruker,
                sakId = sakId,
                oppgaveId = oppgaveId,
            )
        )
    }

    @KafkaEvent(IncomingMessage.EVENT_NAME)
    data class IncomingMessage(
        val journalpostId: String,
        val journalførendeEnhet: String,
        val fnrBruker: Fødselsnummer,
        val oppgaveId: String,
        val sakId: String,
        val dokumentId: String?,
        val dokumenttittel: String?,
        val dokumenter: List<Dokument>?,
        override val eventId: UUID = UUID.randomUUID(),
    ) : KafkaMessage {
        data class Dokument(
            val dokumentId: String,
            val tittel: String,
            val annetInnhold: Set<String> = emptySet(),
        )

        companion object {
            const val EVENT_NAME = "hm-journalpost-journalført"
        }
    }

    @KafkaEvent(OutgoingMessage.EVENT_NAME)
    data class OutgoingMessage(
        val journalpostId: String,
        val journalførendeEnhet: String,
        val nyJournalpostId: String,
        val fnrBruker: String,
        val sakId: String,
        val oppgaveId: String?,
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
