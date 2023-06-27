package no.nav.hjelpemidler.joark.service.hotsak

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.joark.publish
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
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
 * Oppdater og ferdigstill journalpost etter manuell journalføring i Hotsak.
 */
class JournalpostJournalførtOppdaterOgFerdigstillJournalpost(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.demandValue("eventName", "hm-journalpost-journalført") }
                validate {
                    it.requireKey("journalpostId", "journalførendeEnhet", "fnrBruker", "sakId")
                    it.interestedIn("dokumentId", "dokumenttittel")
                }
            }
            .register(this)
    }

    private val JsonMessage.journalpostId: String
        get() = get("journalpostId").textValue()

    private val JsonMessage.journalførendeEnhet: String
        get() = get("journalførendeEnhet").textValue()

    private val JsonMessage.fnrBruker: String
        get() = get("fnrBruker").textValue()

    private val JsonMessage.sakId: String
        get() = get("sakId").textValue()

    private val JsonMessage.dokumentId: String?
        get() = get("dokumentId").textValue()

    private val JsonMessage.dokumenttittel: String?
        get() = get("dokumenttittel").textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val journalpostId = packet.journalpostId
        if (journalpostId in skip) {
            log.warn { "Hopper over journalpostId: $journalpostId" }
            return
        }
        val journalførendeEnhet = packet.journalførendeEnhet
        val fnrBruker = packet.fnrBruker
        val sakId = packet.sakId
        val dokumentId = packet.dokumentId
        val dokumenttittel = packet.dokumenttittel

        log.info {
            "Oppdaterer og ferdigstiller journalpost, journalpostId: $journalpostId, sakId: $sakId"
        }

        val nyJournalpostId = journalpostService.ferdigstillJournalpost(
            journalpostId = journalpostId,
            journalførendeEnhet = journalførendeEnhet,
            fnrBruker = fnrBruker,
            sakId = sakId,
            dokumentId = dokumentId,
            dokumenttittel = dokumenttittel
        )

        context.publish(
            key = fnrBruker,
            message = JournalpostOppdatertOgFerdigstilt(
                journalpostId = journalpostId,
                journalførendeEnhet = journalførendeEnhet,
                nyJournalpostId = nyJournalpostId,
                fnrBruker = fnrBruker,
                sakId = sakId,
            )
        )
    }
}

data class JournalpostOppdatertOgFerdigstilt(
    val eventId: UUID = UUID.randomUUID(),
    val eventName: String = "hm-journalpost-oppdatert-og-ferdigstilt",
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val journalpostId: String,
    val journalførendeEnhet: String,
    val nyJournalpostId: String,
    val fnrBruker: String,
    val sakId: String,
)
