package no.nav.hjelpemidler.joark.service.hotsak

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.joark.jsonMapper
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.saf.hentjournalpost.Journalpost

private val log = KotlinLogging.logger {}
private val secureLog = KotlinLogging.logger("tjenestekall")

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

    private val JsonMessage.journalpostId: List<String>
        get() = this["journalpostId"].let {
            when {
                it.isArray -> it.map(JsonNode::textValue)
                else -> listOf()
            }
        }

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val journalpostId = packet.journalpostId
        log.info {
            "Avstemmer journalposter med journalpostId: $journalpostId"
        }
        try {
            val journalposter: Map<String, Journalpost?> = supervisorScope {
                journalpostId
                    .map {
                        async(Dispatchers.IO) {
                            it to journalpostService.hentJournalpost(it)
                        }
                    }
                    .awaitAll()
                    .toMap()
            }
            secureLog.info { "Hentet journalposter til avstemming: '${jsonMapper.writeValueAsString(journalposter)}'" }
        } catch (e: Exception) {
            log.warn(e) { "Kunne ikke avstemme journalposter med journalpostId: $journalpostId" }
        }
    }
}
