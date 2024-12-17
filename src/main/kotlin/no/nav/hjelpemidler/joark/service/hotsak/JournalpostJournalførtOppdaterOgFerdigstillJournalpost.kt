package no.nav.hjelpemidler.joark.service.hotsak

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.publish
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

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
                precondition { it.requireValue("eventName", "hm-journalpost-journalført") }
                validate {
                    it.requireKey("journalpostId", "journalførendeEnhet", "fnrBruker", "sakId")
                    it.interestedIn("dokumentId", "dokumenttittel", "oppgaveId")
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

    private val JsonMessage.oppgaveId: String?
        get() = get("oppgaveId").textValue()

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
        val oppgaveId = packet.oppgaveId

        log.info {
            "Oppdaterer og ferdigstiller journalpost, journalpostId: $journalpostId, sakId: $sakId, oppgaveId: $oppgaveId"
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
                oppgaveId = oppgaveId,
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
    val oppgaveId: String?,
)

private val skip = setOf(
    "453827301",
    "598126522",
    "609522349",
    "610130874",
    "610767289",
    "611390815",
    "453837166",
    "453901864"
)
