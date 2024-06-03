package no.nav.hjelpemidler.joark.service.hotsak

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService

private val log = KotlinLogging.logger {}

class SakAnnulert(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-sak-annulert") }
            validate { it.requireKey("sakId", "journalpostId") }
        }.register(this)
    }

    private val JsonMessage.sakId get() = this["sakId"].textValue()
    private val JsonMessage.journalpostId get() = this["journalpostId"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val sakId = packet.sakId
        val journalpostId = packet.journalpostId
        log.info { "Sak med sakId: $sakId annulert, feilregistrerer sakstilknytning for journalpostId: $journalpostId" }
        journalpostService.feilregistrerSakstilknytning(journalpostId)
    }
}
