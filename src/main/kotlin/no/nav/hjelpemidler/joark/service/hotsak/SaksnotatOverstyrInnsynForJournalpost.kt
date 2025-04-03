package no.nav.hjelpemidler.joark.service.hotsak

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.joark.service.hotsak.SaksnotatOverstyrInnsynForJournalpost.SaksnotatOverstyrInnsynMessage
import no.nav.hjelpemidler.kafka.KafkaEvent
import no.nav.hjelpemidler.kafka.KafkaMessage
import no.nav.hjelpemidler.rapids_and_rivers.ExtendedMessageContext
import no.nav.hjelpemidler.rapids_and_rivers.KafkaMessageListener
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Overstyr innsynsregler for journalpost for saksnotat.
 */
class SaksnotatOverstyrInnsynForJournalpost(
    private val journalpostService: JournalpostService,
) : KafkaMessageListener<SaksnotatOverstyrInnsynMessage>(
    SaksnotatOverstyrInnsynMessage::class,
    failOnError = true,
) {
    override fun skipMessage(message: JsonMessage, context: ExtendedMessageContext): Boolean = false

    override suspend fun onMessage(message: SaksnotatOverstyrInnsynMessage, context: ExtendedMessageContext) {
        val journalpostId = message.journalpostId
        log.info { "Mottok melding om at saksnotat trenger overstyring av innsyn, journalpostId: $journalpostId" }
        journalpostService.overstyrInnsynForBruker(journalpostId)
    }

    @KafkaEvent(SaksnotatOverstyrInnsynMessage.EVENT_NAME)
    data class SaksnotatOverstyrInnsynMessage(
        val journalpostId: String,
        override val eventId: UUID = UUID.randomUUID(),
    ) : KafkaMessage {
        companion object {
            const val EVENT_NAME = "hm-journalført-notat-overstyr-innsyn"
        }
    }
}
