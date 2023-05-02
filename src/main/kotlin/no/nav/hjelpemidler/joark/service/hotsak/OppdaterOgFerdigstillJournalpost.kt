package no.nav.hjelpemidler.joark.service.hotsak

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.dokarkiv.DokarkivClient
import no.nav.hjelpemidler.dokarkiv.models.AvsenderMottaker
import no.nav.hjelpemidler.dokarkiv.models.Bruker
import no.nav.hjelpemidler.dokarkiv.models.FerdigstillJournalpostRequest
import no.nav.hjelpemidler.dokarkiv.models.OppdaterJournalpostRequest
import no.nav.hjelpemidler.dokarkiv.models.Sak
import no.nav.hjelpemidler.joark.joark.joarkIntegrationException
import no.nav.hjelpemidler.joark.publish
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

class OppdaterOgFerdigstillJournalpost(
    rapidsConnection: RapidsConnection,
    private val dokarkivClient: DokarkivClient,
) : River.PacketListener {
    companion object {
        private val skip = setOf(
            "453827301",
            "598126522",
            "609522349",
            "610130874",
            "610767289",
        )
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
        val journalpostId = packet.journalpostId
        if (skip.contains(journalpostId)) {
            logger.warn { "Hopper over journalpostId: $journalpostId" }
            return
        }
        val fnrBruker = packet.fnrBruker
        val sakId = packet.sakId
        val oppdaterJournalpostRequest = OppdaterJournalpostRequest(
            bruker = Bruker(packet.fnrBruker, Bruker.IdType.FNR),
            tittel = packet.tittel,
            sak = Sak(
                fagsakId = sakId,
                fagsaksystem = Sak.Fagsaksystem.HJELPEMIDLER,
                sakstype = Sak.Sakstype.FAGSAK,
            ),
            avsenderMottaker = AvsenderMottaker(packet.fnrBruker, AvsenderMottaker.IdType.FNR)
        )
        val ferdigstillJournalpostRequest = FerdigstillJournalpostRequest(
            journalfoerendeEnhet = packet.journalførendeEnhet
        )
        logger.info {
            "Oppdaterer og ferdigstiller journalpost, journalpostId: $journalpostId, sakId: $sakId"
        }
        runBlocking(Dispatchers.IO) {
            try {
                dokarkivClient.oppdaterJournalpost(journalpostId, oppdaterJournalpostRequest)
                dokarkivClient.ferdigstillJournalpost(journalpostId, ferdigstillJournalpostRequest)
            } catch (e: ClosedReceiveChannelException) {
                val message =
                    "Feilet under oppdatering eller ferdigstilling av journalpost med journalpostId: $journalpostId"
                logger.error(e) { message }
                joarkIntegrationException(message, e)
            }
        }
        context.publish(
            key = fnrBruker,
            message = JournalpostOppdatertOgFerdigstilt(
                journalpostId = journalpostId,
                journalførendeEnhet = ferdigstillJournalpostRequest.journalfoerendeEnhet,
                tittel = oppdaterJournalpostRequest.tittel,
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
        val tittel: String?,
        val fnrBruker: String,
        val sakId: String,
        val opprettet: LocalDateTime = LocalDateTime.now(),
    )
}
