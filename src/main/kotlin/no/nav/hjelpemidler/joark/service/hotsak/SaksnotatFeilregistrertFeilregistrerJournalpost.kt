package no.nav.hjelpemidler.joark.service.hotsak

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.collections.joinToString
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.joark.service.hotsak.SaksnotatFeilregistrertFeilregistrerJournalpost.SaksnotatFeilregistrertMessage
import no.nav.hjelpemidler.kafka.KafkaEvent
import no.nav.hjelpemidler.kafka.KafkaMessage
import no.nav.hjelpemidler.rapids_and_rivers.ExtendedMessageContext
import no.nav.hjelpemidler.rapids_and_rivers.KafkaMessageListener
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Saksnotat er feilregistrert i Hotsak.
 */
class SaksnotatFeilregistrertFeilregistrerJournalpost(
    private val journalpostService: JournalpostService,
) : KafkaMessageListener<SaksnotatFeilregistrertMessage>(
    SaksnotatFeilregistrertMessage::class,
    failOnError = true,
) {
    override fun skipMessage(message: JsonMessage, context: ExtendedMessageContext): Boolean = false

    override suspend fun onMessage(message: SaksnotatFeilregistrertMessage, context: ExtendedMessageContext) {
        log.info {
            mapOf(
                "sakId" to message.sakId,
                "saksnotatId" to message.saksnotatId,
                "journalpostId" to message.journalpostId,
            ).joinToString(prefix = "Saksnotat er feilregistrert i Hotsak, feilregistrerer sakstilknytning, ")
        }
        journalpostService.feilregistrerSakstilknytning(message.journalpostId)
    }

    @KafkaEvent(SaksnotatFeilregistrertMessage.EVENT_NAME)
    data class SaksnotatFeilregistrertMessage(
        val sakId: String,
        val saksnotatId: String,
        val journalpostId: String,
        override val eventId: UUID,
    ) : KafkaMessage {
        companion object {
            const val EVENT_NAME = "hm-journalført-notat-feilregistrert"
        }
    }
}
