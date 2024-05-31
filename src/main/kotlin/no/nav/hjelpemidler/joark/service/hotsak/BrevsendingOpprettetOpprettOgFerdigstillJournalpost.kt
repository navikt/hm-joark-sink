package no.nav.hjelpemidler.joark.service.hotsak

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.joark.Hendelse
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.domain.Språkkode
import no.nav.hjelpemidler.joark.domain.brevkodeForEttersendelse
import no.nav.hjelpemidler.joark.publish
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService

private val log = KotlinLogging.logger {}

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
                    it.interestedIn("brevsendingId", "opprettetAv")
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

    private val JsonMessage.fysiskDokument: ByteArray
        get() = this["fysiskDokument"].binaryValue()

    private val JsonMessage.dokumenttittel: String
        get() = this["dokumenttittel"].textValue()

    private val JsonMessage.dokumenttype: Dokumenttype
        get() = this["dokumenttype"].textValue().let(Dokumenttype::valueOf)

    private val JsonMessage.språkkode: Språkkode
        get() = this["språkkode"].textValue().let(Språkkode::valueOf)

    private val JsonMessage.brevsendingId: String?
        get() = this["brevsendingId"].textValue()

    private val JsonMessage.opprettetAv: String?
        get() = this["opprettetAv"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val sakId = packet.sakId
        val fnrMottaker = packet.fnrMottaker
        val fnrBruker = packet.fnrBruker
        val dokumenttittel = packet.dokumenttittel
        val dokumenttype = packet.dokumenttype
        val brevsendingId = packet.brevsendingId
        val opprettetAv = packet.opprettetAv

        log.info { "Mottok melding om at brevsending er opprettet, sakId: $sakId, dokumenttype: $dokumenttype, brevsendingId: $brevsendingId" }

        val fysiskDokument = when (val brevkode = brevkodeForEttersendelse[dokumenttype]) {
            null -> packet.fysiskDokument
            else -> journalpostService.genererFørsteside(packet.dokumenttittel, fnrBruker, packet.fysiskDokument) {
                this.språkkode = packet.språkkode
                this.brevkode = brevkode
            }
        }

        val journalpostId = journalpostService.opprettUtgåendeJournalpost(
            fnrMottaker = fnrMottaker,
            fnrBruker = fnrBruker,
            dokumenttype = dokumenttype,
            forsøkFerdigstill = true,
        ) {
            dokument(fysiskDokument)
            hotsak(sakId)
            this.opprettetAv = opprettetAv
        }

        @Hendelse("hm-brevsending-journalført")
        data class BrevsendingJournalførtHendelse(
            val journalpostId: String,
            val sakId: String,
            val fnrMottaker: String,
            val fnrBruker: String,
            val dokumenttittel: String,
            val dokumenttype: Dokumenttype,
            val brevsendingId: String?,
            val opprettetAv: String?,
        )

        context.publish(
            key = fnrBruker,
            message = BrevsendingJournalførtHendelse(
                journalpostId = journalpostId,
                sakId = sakId,
                fnrMottaker = fnrMottaker,
                fnrBruker = fnrBruker,
                dokumenttittel = dokumenttittel,
                dokumenttype = dokumenttype,
                brevsendingId = brevsendingId,
                opprettetAv = opprettetAv,
            )
        )
    }
}
