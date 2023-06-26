package no.nav.hjelpemidler.joark.service.hotsak

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.joark.publish
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.saf.enums.Journalstatus
import no.nav.hjelpemidler.saf.enums.Kanal
import no.nav.hjelpemidler.saf.enums.SkjermingType
import no.nav.hjelpemidler.saf.enums.Tema
import no.nav.hjelpemidler.saf.enums.Variantformat
import no.nav.hjelpemidler.saf.hentjournalpost.DokumentInfo
import no.nav.hjelpemidler.saf.hentjournalpost.Dokumentvariant
import no.nav.hjelpemidler.saf.hentjournalpost.LogiskVedlegg
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

        val journalpost = journalpostService.ferdigstillJournalpost(
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
                nyJournalpostId = journalpost.journalpostId,
                fnrBruker = fnrBruker,
                sakId = sakId,
                tittel = journalpost.tittel,
                ferdigstiltJournalpost = JournalpostOppdatertOgFerdigstilt.Journalpost(
                    journalpost = journalpost,
                    journalførendeEnhet = journalførendeEnhet,
                )
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
    val tittel: String?,
    val ferdigstiltJournalpost: Journalpost,
) {
    data class Journalpost(
        val tema: Tema?,
        val journalpostOpprettet: LocalDateTime,
        val opprettetAvEnhet: String = "9999",
        val journalførendeEnhet: String?,
        val tittel: String?,
        val kanal: Kanal?,
        val dokumenter: List<Dokument> = emptyList(),
        val status: Journalstatus?,
    ) {
        constructor(
            journalpost: no.nav.hjelpemidler.saf.hentjournalpost.Journalpost,
            journalførendeEnhet: String?,
        ) : this(
            tema = journalpost.tema,
            journalpostOpprettet = journalpost.datoOpprettet,
            journalførendeEnhet = journalførendeEnhet ?: journalpost.journalfoerendeEnhet,
            tittel = journalpost.tittel,
            kanal = journalpost.kanal,
            dokumenter = journalpost.dokumenter
                ?.filterNotNull()
                ?.map(JournalpostOppdatertOgFerdigstilt::Dokument) ?: emptyList(),
            status = journalpost.journalstatus,
        )
    }

    data class Dokument(
        val dokumentId: String,
        val tittel: String?,
        val brevkode: String?,
        val skjerming: String?,
        val vedlegg: List<Vedlegg> = emptyList(),
        val varianter: List<Variant> = emptyList(),
    ) {
        constructor(dokument: DokumentInfo) : this(
            dokumentId = dokument.dokumentInfoId,
            tittel = dokument.tittel,
            brevkode = dokument.brevkode,
            skjerming = dokument.skjerming,
            vedlegg = dokument.logiskeVedlegg.filterNotNull().map(JournalpostOppdatertOgFerdigstilt::Vedlegg),
            varianter = dokument.dokumentvarianter.filterNotNull().map(JournalpostOppdatertOgFerdigstilt::Variant)
        )
    }

    data class Vedlegg(
        val vedleggId: String,
        val tittel: String?,
    ) {
        constructor(vedlegg: LogiskVedlegg) : this(
            vedleggId = vedlegg.logiskVedleggId,
            tittel = vedlegg.tittel,
        )
    }

    data class Variant(
        val format: Variantformat,
        val skjerming: SkjermingType?,
    ) {
        constructor(variant: Dokumentvariant) : this(
            format = variant.variantformat,
            skjerming = variant.skjerming,
        )
    }
}
