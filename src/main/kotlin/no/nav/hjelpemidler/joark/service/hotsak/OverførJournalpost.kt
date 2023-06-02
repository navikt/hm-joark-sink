package no.nav.hjelpemidler.joark.service.hotsak

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

class OverførJournalpost(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-journalpost-overført") }
            validate {
                it.requireKey("journalpostId")
            }
        }.register(this)
    }

    private val JsonMessage.journalpostId get() = this["journalpostId"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val journalpostId = packet.journalpostId
        log.info {
            "Overfører journalpost med journalpostId: $journalpostId"
        }
        try {
            val nyEksternReferanseId = "${journalpostId}_${LocalDateTime.now()}_GOSYS_TIL_HOTSAK"
            val nyJournalpostId = journalpostService.kopierJournalpost(
                journalpostId = journalpostId,
                nyEksternReferanseId = nyEksternReferanseId
            )
            log.info {
                "Journalpost til overføring opprettet, journalpostId: $journalpostId, nyJournalpostId: $nyJournalpostId, nyEksternReferanseId: $nyEksternReferanseId"
            }
        } catch (e: Exception) {
            log.warn(e) {
                "Overføring feilet, kunne ikke kopiere journalpost med journalpostId: $journalpostId"
            }
        }
    }
}
