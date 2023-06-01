package no.nav.hjelpemidler.joark.service.hotsak

import com.fasterxml.jackson.module.kotlin.convertValue
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.joark.jsonMapper
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService

private val log = KotlinLogging.logger {}

class AvstemJournalpost(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-journalpost-avstemming") }
            validate {
                it.requireKey("journalpostId")
            }
        }.register(this)
    }

    private val JsonMessage.journalpostId get() = this["journalpostId"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val journalpostId = packet.journalpostId
        log.info {
            "Avstemmer journalpost med journalpostId: $journalpostId"
        }
        try {
            val journalpost = journalpostService.hentJournalpost(journalpostId)
            when (journalpost) {
                null -> log.warn { "Fant ikke journalpost med journalpostId: $journalpostId" }
                else -> log.info {
                    val egenskaper = jsonMapper
                        .convertValue<Map<String, Any?>>(journalpost)
                        .minus(setOf("bruker", "avsenderMottaker", "dokumenter"))
                    "Hentet journalpost med journalpostId: $journalpostId, egenskaper: $egenskaper"
                }
            }
        } catch (e: Exception) {
            log.warn(e) { "Kunne ikke avstemme journalpost med journalpostId: $journalpostId" }
        }
    }
}
