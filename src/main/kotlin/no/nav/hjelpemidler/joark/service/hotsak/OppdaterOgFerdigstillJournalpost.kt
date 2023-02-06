package no.nav.hjelpemidler.joark.service.hotsak

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.joark.joark.JoarkClientV3
import no.nav.hjelpemidler.joark.joark.JoarkClientV3.FerdigstiltJournalpost
import no.nav.hjelpemidler.joark.joark.JoarkClientV3.OppdatertJournalpost
import no.nav.hjelpemidler.joark.joark.model.Bruker
import no.nav.hjelpemidler.joark.joark.model.Sak
import no.nav.hjelpemidler.joark.publish
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

class OppdaterOgFerdigstillJournalpost(
    rapidsConnection: RapidsConnection,
    private val joarkClient: JoarkClientV3,
) : River.PacketListener {
    companion object {
        private val skip = setOf("573814262", "453824415")
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-journalpost-journalført") }
            validate { it.requireKey("journalpostId", "journalførendeEnhet", "tittel", "fnrBruker", "sakId") }
        }.register(this)
    }

    private val JsonMessage.journalpostId: String
        get() = get("journalpostId").textValue()

    private val JsonMessage.journalførendeEnhet: String
        get() = get("journalførendeEnhet").textValue()

    private val JsonMessage.tittel: String
        get() = get("tittel").textValue()

    private val JsonMessage.fnrBruker: String
        get() = get("fnrBruker").textValue()

    private val JsonMessage.sakId: String
        get() = get("sakId").textValue()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        if (packet.journalpostId == "453827290") {
            return
        }
        val journalpostId = packet.journalpostId
        if (skip.contains(journalpostId)) {
            logger.warn { "Hopper over journalpostId: $journalpostId" }
            return
        }
        val fnrBruker = packet.fnrBruker
        val sakId = packet.sakId
        val oppdatertJournalpost = OppdatertJournalpost(
            journalpostId = journalpostId,
            bruker = Bruker(packet.fnrBruker, "FNR"),
            tittel = packet.tittel,
            sak = Sak.hotsak(sakId)
        )
        val ferdigstiltJournalpost = FerdigstiltJournalpost(
            journalpostId = journalpostId,
            journalførendeEnhet = packet.journalførendeEnhet
        )
        logger.info {
            "Oppdaterer og ferdigstiller journalpost, journalpostId: $journalpostId, sakId: $sakId"
        }
        runBlocking(Dispatchers.IO) {
            joarkClient.oppdaterJournalpost(oppdatertJournalpost)
            joarkClient.ferdigstillJournalpost(ferdigstiltJournalpost)
        }
        context.publish(
            key = fnrBruker,
            message = JournalpostOppdatertOgFerdigstilt(
                journalpostId = journalpostId,
                journalførendeEnhet = ferdigstiltJournalpost.journalførendeEnhet,
                tittel = oppdatertJournalpost.tittel,
                fnrBruker = fnrBruker,
                sakId = sakId,
            )
        )
    }

    data class JournalpostOppdatertOgFerdigstilt(
        val eventId: UUID = UUID.randomUUID(),
        val eventName: String = "hm-journalpost-oppdatert-og-ferdigstilt",
        val journalpostId: String,
        val journalførendeEnhet: String,
        val tittel: String,
        val fnrBruker: String,
        val sakId: String,
        val opprettet: LocalDateTime = LocalDateTime.now(),
    )
}
