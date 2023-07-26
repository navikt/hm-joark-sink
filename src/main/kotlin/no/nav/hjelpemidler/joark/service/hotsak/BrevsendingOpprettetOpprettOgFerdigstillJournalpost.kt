package no.nav.hjelpemidler.joark.service.hotsak

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.domain.Dokumenttype
import no.nav.hjelpemidler.domain.Språkkode
import no.nav.hjelpemidler.joark.Hendelse
import no.nav.hjelpemidler.joark.publish
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService

class BrevsendingOpprettetOpprettOgFerdigstillJournalpost(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.demandValue("eventName", "hm-brevsending-opprettet") }
                validate {
                    it.requireKey(
                        "sakId",
                        "fnrMottaker",
                        "fnrBruker",
                        "fysiskDokument",
                        "dokumenttittel",
                        "dokumenttype",
                        "språkkode",
                    )
                }
            }
            .register(this)
    }

    private val JsonMessage.sakId: String
        get() = this["sakId"].textValue()

    private val JsonMessage.fnrMottaker: String
        get() = this["fnrMottaker"].textValue()

    private val JsonMessage.fnrBruker: String
        get() = this["fnrBruker"].textValue()

    private val JsonMessage.fysiskDokument: String
        get() = this["fysiskDokument"].textValue()

    private val JsonMessage.dokumenttittel: String
        get() = this["dokumenttittel"].textValue()

    private val JsonMessage.dokumenttype: Dokumenttype
        get() = this["dokumenttype"].textValue().let(Dokumenttype::valueOf)

    private val JsonMessage.språkkode: Språkkode
        get() = this["språkkode"].textValue().let(Språkkode::valueOf)

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val sakId = packet.sakId
        val fnrMottaker = packet.fnrMottaker
        val fnrBruker = packet.fnrBruker
        val fysiskDokument = packet.fysiskDokument
        val dokumenttittel = packet.dokumenttittel
        val dokumenttype = packet.dokumenttype
        val språkkode = packet.språkkode

        val journalpostId = journalpostService.opprettUtgåendeJournalpost(
            fnrMottaker = fnrMottaker,
            fnrBruker = fnrBruker,
            dokumenttype = dokumenttype,
            forsøkFerdigstill = true,
            lagFørsteside = true, // fixme -> baser på dokumenttype?
        ) {
            dokument(
                fysiskDokument = fysiskDokument.toByteArray(),
                dokumenttittel = dokumenttittel,
            )
            hotsak(sakId)
            tittel = dokumenttittel
        }

        @Hendelse("hm-brevsending-journalført")
        data class BrevsendingJournalførtHendelse(
            val journalpostId: String,
            val sakId: String,
            val fnrMottaker: String,
            val fnrBruker: String,
            val dokumenttittel: String,
            val dokumenttype: Dokumenttype,
        )

        context.publish(
            key = fnrBruker,
            message = BrevsendingJournalførtHendelse(
                journalpostId = journalpostId,
                sakId = sakId,
                fnrMottaker = packet.fnrMottaker,
                fnrBruker = fnrBruker,
                dokumenttittel = dokumenttittel,
                dokumenttype = dokumenttype,
            )
        )
    }
}
