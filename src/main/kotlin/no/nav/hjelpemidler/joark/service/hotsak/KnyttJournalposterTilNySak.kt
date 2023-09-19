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

/**
 * Knytter journalposter fra eksisterende sak til ny sak
 */
class KnyttJournalposterTilNySak(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.demandValue("eventName", "hm-journalposter-knyttet-til-ny-sak") }
                validate {
                    it.requireKey("fraSakId", "tilSakId", "fnrBruker", "journalførendeEnhet")
                }
            }
            .register(this)
    }

    private val JsonMessage.fraSakId: String
        get() = get("fraSakId").textValue()

    private val JsonMessage.tilSakId: String
        get() = get("tilSakId").textValue()

    private val JsonMessage.fnrBruker: String
        get() = get("fnrBruker").textValue()

    private val JsonMessage.journalførendeEnhet: String
        get() = get("journalførendeEnhet").textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val fraSakId = packet.fraSakId
        val tilSakId = packet.tilSakId
        val fnrBruker = packet.fnrBruker
        val journalførendeEnhet = packet.journalførendeEnhet

        log.info {
            "Knytter journalposter for sakId: $fraSakId til sakId: $tilSakId"
        }

        val journalposter = journalpostService.hentJournalposterSak(fraSakId)

        log.info { "${journalposter.map { it.journalpostId }} knyttes til sakId: $tilSakId" }

        val nyeJournalposter = journalposter.map {
            it.journalpostId to journalpostService.ferdigstillJournalpost(
                journalpostId = it.journalpostId,
                journalførendeEnhet = journalførendeEnhet,
                fnrBruker = fnrBruker,
                sakId = tilSakId,
                dokumentId = null,
                dokumenttittel = null
            )
        }

        val nyJournalpostIdByJournalpostId = nyeJournalposter.toMap()

        log.info { "$nyJournalpostIdByJournalpostId, sakId: $tilSakId" }

        context.publish(
            key = fnrBruker,
            message = JournalposterTilknyttetSak(
                fraSakId = fraSakId,
                tilSakId = tilSakId,
                fnrBruker = fnrBruker,
                journalførendeEnhet = journalførendeEnhet,
                journalposter = nyJournalpostIdByJournalpostId
            )
        )
    }
}

data class JournalposterTilknyttetSak(
    val eventId: UUID = UUID.randomUUID(),
    val eventName: String = "hm-journalposter-tilknyttet-sak",
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val fraSakId: String,
    val tilSakId: String,
    val fnrBruker: String,
    val journalførendeEnhet: String,
    val journalposter: Map<String, String>,
)