package no.nav.hjelpemidler.joark.service.hotsak

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.saf.tekst

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

    private val JsonMessage.journalpostId: List<String>
        get() = this["journalpostId"].map {
            it.textValue()
        }

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val journalpostId = packet.journalpostId
        log.info {
            "Avstemmer journalposter med journalpostId: $journalpostId"
        }
        try {
            val journalposter: Map<String, String> = supervisorScope {
                journalpostId
                    .map {
                        async(Dispatchers.IO) {
                            it to journalpostService.hentJournalpost(it)
                        }
                    }
                    .awaitAll()
                    .toMap()
                    .mapValues { it.value.tekst() }
            }
            log.info { "Hentet journalposter til avstemming, $journalposter" }
        } catch (e: Exception) {
            log.warn(e) { "Kunne ikke avstemme journalposter med journalpostId: $journalpostId" }
        }
    }
}
