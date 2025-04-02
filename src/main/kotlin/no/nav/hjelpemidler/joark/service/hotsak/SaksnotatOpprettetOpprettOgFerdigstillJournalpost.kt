package no.nav.hjelpemidler.joark.service.hotsak

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.collections.joinToString
import no.nav.hjelpemidler.configuration.HotsakApplicationId
import no.nav.hjelpemidler.domain.id.URN
import no.nav.hjelpemidler.domain.person.Fødselsnummer
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.kafka.KafkaEvent
import no.nav.hjelpemidler.kafka.KafkaMessage
import no.nav.hjelpemidler.rapids_and_rivers.ExtendedMessageContext
import no.nav.hjelpemidler.rapids_and_rivers.KafkaMessageListener
import java.util.UUID

private val log = KotlinLogging.logger {}

class SaksnotatOpprettetOpprettOgFerdigstillJournalpost(
    private val journalpostService: JournalpostService,
) : KafkaMessageListener<SaksnotatOpprettetOpprettOgFerdigstillJournalpost.IncomingMessage>(
    IncomingMessage::class,
    failOnError = true,
) {
    override fun skipMessage(message: JsonMessage, context: ExtendedMessageContext): Boolean = false

    override suspend fun onMessage(message: IncomingMessage, context: ExtendedMessageContext) {
        val sakId = message.sakId
        val saksnotatId = message.saksnotatId
        val fnrBruker = message.fnrBruker
        val dokumenttittel = message.dokumenttittel
        val opprettetAv = message.opprettetAv

        log.info {
            mapOf(
                "sakId" to sakId,
                "saksnotatId" to saksnotatId,
                "dokumenttype" to Dokumenttype.NOTAT,
                "inkludererStrukturertDokument" to (message.strukturertDokument != null)
            ).joinToString(prefix = "Mottok melding om at saksnotat er opprettet, ")
        }

        val journalpost = journalpostService.opprettNotat(
            fnrBruker = fnrBruker,
            eksternReferanseId = URN(
                applicationId = HotsakApplicationId,
                resource = "saksnotat",
                id = saksnotatId,
            ),
        ) {
            this.tittel = dokumenttittel
            this.opprettetAv = opprettetAv
            dokument(
                fysiskDokument = message.fysiskDokument,
                dokumenttittel = dokumenttittel,
                strukturertDokument = message.strukturertDokument,
            )
            hotsak(sakId)
            tilleggsopplysninger(
                "sakId" to sakId,
                "saksnotatId" to saksnotatId,
                prefix = HotsakApplicationId.application,
            )
        }

        context.publish(
            key = fnrBruker.toString(),
            message = OutgoingMessage(
                journalpostId = journalpost.journalpostId,
                dokumentId = journalpost.dokumentIder.single(),
                sakId = sakId,
                saksnotatId = saksnotatId,
                fnrBruker = fnrBruker,
                dokumenttittel = dokumenttittel,
                dokumenttype = Dokumenttype.NOTAT,
                opprettetAv = opprettetAv,
            )
        )
    }

    @KafkaEvent(IncomingMessage.EVENT_NAME)
    data class IncomingMessage(
        val sakId: String,
        val saksnotatId: String,
        val fnrBruker: Fødselsnummer,
        val dokumenttittel: String,
        val fysiskDokument: ByteArray,
        val strukturertDokument: JsonNode?,
        val opprettetAv: String,
        override val eventId: UUID,
    ) : KafkaMessage {
        companion object {
            const val EVENT_NAME = "hm-journalført-notat-opprettet"
        }
    }

    @KafkaEvent(OutgoingMessage.EVENT_NAME)
    data class OutgoingMessage(
        val journalpostId: String,
        val dokumentId: String,
        val sakId: String,
        val saksnotatId: String,
        val fnrBruker: Fødselsnummer,
        val dokumenttittel: String,
        val dokumenttype: Dokumenttype,
        val opprettetAv: String,
        override val eventId: UUID = UUID.randomUUID(),
    ) : KafkaMessage {
        companion object {
            const val EVENT_NAME = "hm-journalført-notat-journalført"
        }
    }
}
