package no.nav.hjelpemidler.joark.service.barnebriller

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.serialization.jackson.uuidValue
import java.util.UUID

private val log = KotlinLogging.logger {}

class FeilregistrerJournalpostBarnebriller(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "hm-barnebriller-feilregistrer-journalpost") }
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

    private val JsonMessage.eventId get() = this["eventId"].uuidValue()
    private val JsonMessage.sakId get() = this["sakId"].textValue()
    private val JsonMessage.journalpostId get() = this["joarkRef"].textValue()
    private val JsonMessage.opprettet get() = this["opprettet"].asLocalDateTime()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val eventId = packet.eventId
        val sakId = packet.sakId
        val journalpostId = packet.journalpostId
        val opprettet = packet.opprettet
        if (eventId in skip) {
            log.warn {
                "Hopper over hendelse med eventId: $eventId, sakId: $sakId, journalpostId: $journalpostId, , opprettet: $opprettet"
            }
            return
        }
        log.info {
            "Feilregistrerer barnebrillesak, eventId: $eventId, sakId: $sakId, journalpostId: $journalpostId, opprettet: $opprettet"
        }
        journalpostService.feilregistrerSakstilknytning(journalpostId)
    }
}

private val skip = setOf(
    "78980925-7da0-4ddc-8d4d-8db40c70f499",
).map(UUID::fromString)
