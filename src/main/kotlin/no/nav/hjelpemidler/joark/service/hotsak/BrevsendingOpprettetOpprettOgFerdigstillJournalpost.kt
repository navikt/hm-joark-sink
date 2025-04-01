package no.nav.hjelpemidler.joark.service.hotsak

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.domain.Språkkode
import no.nav.hjelpemidler.joark.domain.brevkodeForEttersendelse
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.kafka.KafkaEvent
import no.nav.hjelpemidler.kafka.KafkaMessage
import no.nav.hjelpemidler.rapids_and_rivers.publish
import java.util.UUID

private val log = KotlinLogging.logger {}

class BrevsendingOpprettetOpprettOgFerdigstillJournalpost(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("eventName", "hm-brevsending-opprettet") }
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
            eksternReferanseId = "${sakId}_${brevsendingId}",
            forsøkFerdigstill = true,
        ) {
            dokument(fysiskDokument = fysiskDokument)
            hotsak(sakId)
            this.opprettetAv = opprettetAv
        }

        @KafkaEvent("hm-brevsending-journalført")
        data class BrevsendingJournalførtHendelse(
            val journalpostId: String,
            val sakId: String,
            val fnrMottaker: String,
            val fnrBruker: String,
            val dokumenttittel: String,
            val dokumenttype: Dokumenttype,
            val brevsendingId: String?,
            val opprettetAv: String?,
            override val eventId: UUID = UUID.randomUUID(),
        ) : KafkaMessage

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
