package no.nav.hjelpemidler.joark.service.hotsak

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
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
                        "dokumenttittel"
                    )
                    it.interestedIn()
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

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val sakId = packet.sakId
        val fnrMottaker = packet.fnrMottaker
        val fnrBruker = packet.fnrBruker
        val dokumenttittel = packet.dokumenttittel
        val journalpostId = journalpostService.opprettUtgåendeJournalpost(
            fnrMottaker = fnrMottaker,
            fnrBruker = fnrBruker,
            forsøkFerdigstill = true
        ) {
            dokument(
                fysiskDokument = packet.fysiskDokument.toByteArray(),
                dokumenttittel = dokumenttittel,
            )
            sakFraHotsak(sakId)
            tittel = dokumenttittel
        }

        @Hendelse("hm-brevsending-journalført")
        data class BrevsendingJournalførtHendelse(
            val journalpostId: String,
            val sakId: String,
            val fnrMottaker: String,
            val fnrBruker: String,
            val dokumenttittel: String,
        )

        context.publish(
            key = fnrMottaker,
            message = BrevsendingJournalførtHendelse(
                journalpostId = journalpostId,
                sakId = sakId,
                fnrMottaker = packet.fnrMottaker,
                fnrBruker = fnrBruker,
                dokumenttittel = dokumenttittel,
            )
        )
    }
}
