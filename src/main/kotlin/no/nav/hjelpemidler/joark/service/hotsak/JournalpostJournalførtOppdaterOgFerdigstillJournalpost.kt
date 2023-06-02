package no.nav.hjelpemidler.joark.service.hotsak

import kotlinx.coroutines.channels.ClosedReceiveChannelException
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.dokarkiv.DokarkivClient
import no.nav.hjelpemidler.dokarkiv.avsenderMottakerMedFnr
import no.nav.hjelpemidler.dokarkiv.brukerMedFnr
import no.nav.hjelpemidler.dokarkiv.dokarkivError
import no.nav.hjelpemidler.dokarkiv.models.FerdigstillJournalpostRequest
import no.nav.hjelpemidler.dokarkiv.models.OppdaterJournalpostRequest
import no.nav.hjelpemidler.dokarkiv.models.Sak
import no.nav.hjelpemidler.joark.publish
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.saf.enums.Tema
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}
private val skip = setOf(
    "453827301",
    "598126522",
    "609522349",
    "610130874",
    "610767289",
    "611390815",
    "453837166"
)

/**
 * Oppdater og ferdigstill journalpost etter journalføring i Hotsak.
 */
class JournalpostJournalførtOppdaterOgFerdigstillJournalpost(
    rapidsConnection: RapidsConnection,
    private val dokarkivClient: DokarkivClient,
) : AsyncPacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.demandValue("eventName", "hm-journalpost-journalført") }
                validate { it.requireKey("journalpostId", "journalførendeEnhet", "tittel", "fnrBruker", "sakId") }
            }
            .register(this)
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

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val journalpostId = packet.journalpostId
        if (journalpostId in skip) {
            log.warn { "Hopper over journalpostId: $journalpostId" }
            return
        }
        val fnrBruker = packet.fnrBruker
        val sakId = packet.sakId
        val oppdaterJournalpostRequest = OppdaterJournalpostRequest(
            avsenderMottaker = avsenderMottakerMedFnr(packet.fnrBruker),
            bruker = brukerMedFnr(packet.fnrBruker),
            tittel = packet.tittel,
            tema = Tema.HJE.toString(),
            sak = Sak(
                fagsakId = sakId,
                fagsaksystem = Sak.Fagsaksystem.HJELPEMIDLER,
                sakstype = Sak.Sakstype.FAGSAK,
            ),
        )
        val ferdigstillJournalpostRequest = FerdigstillJournalpostRequest(
            journalfoerendeEnhet = packet.journalførendeEnhet
        )
        log.info {
            "Oppdaterer og ferdigstiller journalpost, journalpostId: $journalpostId, sakId: $sakId"
        }
        try {
            dokarkivClient.oppdaterJournalpost(journalpostId, oppdaterJournalpostRequest)
            dokarkivClient.ferdigstillJournalpost(journalpostId, ferdigstillJournalpostRequest)
        } catch (e: ClosedReceiveChannelException) {
            val message =
                "Feilet under oppdatering eller ferdigstilling av journalpost med journalpostId: $journalpostId"
            log.error(e) { message }
            dokarkivError(message, e)
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
