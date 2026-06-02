package no.nav.hjelpemidler.joark.service.hotsak

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.configuration.HotsakApplicationId
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.domain.Språkkode
import no.nav.hjelpemidler.joark.domain.brevkodeForEttersendelse
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.kafka.KafkaEvent
import no.nav.hjelpemidler.kafka.KafkaMessage
import no.nav.hjelpemidler.logging.logStatement
import no.nav.hjelpemidler.rapids_and_rivers.publish
import no.nav.hjelpemidler.serialization.jackson.enumValue
import no.nav.hjelpemidler.serialization.jackson.stringValueOrNull
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
                        "mottakertype",
                        "fnrBruker",
                        "fysiskDokument",
                        "dokumenttittel",
                        "dokumenttype",
                        "språkkode",
                        "brevsendingId",
                    )
                    it.interestedIn("opprettetAv", "brevId", "brevdistribusjonId")
                }
            }
            .register(this)
    }

    private val JsonMessage.sakId: String
        get() = this["sakId"].stringValue()

    private val JsonMessage.brevId: String?
        get() = this["brevId"].stringValueOrNull()

    private val JsonMessage.brevdistribusjonId: String?
        get() = this["brevdistribusjonId"].stringValueOrNull()

    private val JsonMessage.fnrMottaker: String
        get() = this["fnrMottaker"].stringValue()

    private val JsonMessage.mottakertype: String
        get() = this["mottakertype"].stringValue()

    private val JsonMessage.fnrBruker: String
        get() = this["fnrBruker"].stringValue()

    private val JsonMessage.fysiskDokument: ByteArray
        get() = this["fysiskDokument"].binaryValue()

    private val JsonMessage.dokumenttittel: String
        get() = this["dokumenttittel"].stringValue()

    private val JsonMessage.dokumenttype: Dokumenttype
        get() = this["dokumenttype"].enumValue<Dokumenttype>()

    private val JsonMessage.språkkode: Språkkode
        get() = this["språkkode"].enumValue<Språkkode>()

    @Deprecated("Fjernes")
    private val JsonMessage.brevsendingId: String
        get() = this["brevsendingId"].stringValue()

    private val JsonMessage.opprettetAv: String?
        get() = this["opprettetAv"].stringValueOrNull()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val sakId = packet.sakId
        val brevId = packet.brevId
        val brevdistribusjonId = packet.brevdistribusjonId
        val fnrMottaker = packet.fnrMottaker
        val mottakertype = packet.mottakertype
        val fnrBruker = packet.fnrBruker
        val dokumenttittel = packet.dokumenttittel
        val dokumenttype = packet.dokumenttype
        val brevsendingId = packet.brevsendingId
        val opprettetAv = packet.opprettetAv

        log.info {
            logStatement(
                "Mottok melding om at brevsending er opprettet",
                "sakId" to sakId,
                "brevId" to brevId,
                "brevdistribusjonId" to brevdistribusjonId,
                "brevsendingId" to brevsendingId,
                "mottakertype" to mottakertype,
                "dokumenttype" to dokumenttype,
            )
        }

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
            eksternReferanseId = "${sakId}_${brevsendingId}_${mottakertype}",
            forsøkFerdigstill = true,
        ) {
            dokument(fysiskDokument = fysiskDokument, dokumenttittel = dokumenttittel)
            hotsak(sakId)
            tilleggsopplysninger(
                "sakId" to sakId,
                "brevId" to brevId,
                "brevdistribusjonId" to brevdistribusjonId,
                prefix = HotsakApplicationId.application,
            )
            this.opprettetAv = opprettetAv
        }

        @KafkaEvent("hm-brevsending-journalført")
        data class BrevsendingJournalførtHendelse(
            val journalpostId: String,
            val sakId: String,
            val brevId: String?,
            val brevdistribusjonId: String?,
            val fnrMottaker: String,
            val fnrBruker: String,
            val dokumenttittel: String,
            val dokumenttype: Dokumenttype,
            @Deprecated("Fjernes")
            val brevsendingId: String,
            val opprettetAv: String?,
            override val eventId: UUID = UUID.randomUUID(),
        ) : KafkaMessage

        context.publish(
            key = fnrBruker,
            message = BrevsendingJournalførtHendelse(
                journalpostId = journalpostId,
                sakId = sakId,
                brevId = brevId,
                brevdistribusjonId = brevdistribusjonId,
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
