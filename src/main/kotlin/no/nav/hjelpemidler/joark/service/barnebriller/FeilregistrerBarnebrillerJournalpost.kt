package no.nav.hjelpemidler.joark.service.barnebriller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.joark.metrics.Prometheus
import no.nav.hjelpemidler.joark.service.JoarkService
import no.nav.hjelpemidler.joark.service.PacketListenerWithOnError
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal class FeilregistrerBarnebrillerJournalpost(
    rapidsConnection: RapidsConnection,
    private val joarkService: JoarkService,
    private val eventName: String = "hm-barnebriller-feilregistrer-journalpost",
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", eventName) }
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

    private val JsonMessage.eventId get() = this["eventId"].textValue().let { UUID.fromString(it)!! }
    private val JsonMessage.sakId get() = this["sakId"].textValue()!!
    private val JsonMessage.joarkRef get() = this["joarkRef"].textValue()!!
    private val JsonMessage.opprettet get() = this["opprettet"].asLocalDateTime()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        if (skipEvent(packet.eventId)) {
            return
        }

        runBlocking(Dispatchers.IO) {
            logger.info("Feilregistrerer barnebrille-sak: eventId=${packet.eventId}, sakId=${packet.sakId}, joarkRef=${packet.joarkRef}, opprettet=${packet.opprettet}")
            try {
                feilregistrerJournalpost(packet.sakId, packet.joarkRef)
            } catch (e: Exception) {
                logger.error(e) { e.message }
                if (e.message != null && e.message!!.contains("409 Conflict")) return@runBlocking
                throw e
            }
        }
    }

    private suspend fun feilregistrerJournalpost(
        sakId: String,
        joarkRef: String,
    ) =
        runCatching {
            joarkService.feilregistrerJournalpost(joarkRef)
        }.onSuccess {
            logger.info("Feilregistrerte barnebrille-sakstilknytning for sakId=$sakId, joarkRef=$joarkRef")
            Prometheus.feilregistrerteSakstilknytningForJournalpostCounter.inc()
        }.onFailure {
            logger.error(it) { "Feilet under feilregistrering av barnebrille-sakstilknytning for journalpost: sakId=$sakId, joarkRef=$joarkRef" }
            throw it
        }.getOrThrow()

    private fun skipEvent(eventId: UUID): Boolean {
        val eventsToSkip = listOf<String>()
        return eventsToSkip.contains(eventId.toString())
    }
}
