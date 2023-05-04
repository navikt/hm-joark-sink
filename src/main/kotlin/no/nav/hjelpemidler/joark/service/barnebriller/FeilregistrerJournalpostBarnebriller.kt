package no.nav.hjelpemidler.joark.service.barnebriller

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import java.util.UUID

private val log = KotlinLogging.logger {}

class FeilregistrerJournalpostBarnebriller(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-barnebriller-feilregistrer-journalpost") }
            validate {
                it.requireKey(
                    "eventId",
                    "sakId",
                    "joarkRef",
                    "opprettet"
                )
            }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue().let(UUID::fromString)
    private val JsonMessage.sakId get() = this["sakId"].textValue()
    private val JsonMessage.journalpostId get() = this["joarkRef"].textValue()
    private val JsonMessage.opprettet get() = this["opprettet"].asLocalDateTime()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val eventId = packet.eventId
        val journalpostId = packet.journalpostId
        if (skip(eventId)) {
            log.warn {
                "Hopper over hendelse med eventId: $eventId, sakId: ${packet.sakId}, journalpostId: $journalpostId"
            }
            return
        }
        log.info {
            "Feilregistrerer barnebrillesak, eventId: $eventId, sakId: ${packet.sakId}, journalpostId: $journalpostId, opprettet: ${packet.opprettet}"
        }
        journalpostService.feilregistrerSakstilknytning(journalpostId)
    }
}

private fun skip(eventId: UUID): Boolean =
    eventId in setOf<UUID>()
