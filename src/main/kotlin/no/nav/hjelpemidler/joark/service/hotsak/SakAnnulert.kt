package no.nav.hjelpemidler.joark.service.hotsak

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
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
