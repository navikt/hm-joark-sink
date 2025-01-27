package no.nav.hjelpemidler.joark.service.hotsak

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.Hendelse
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.domain.Språkkode
import no.nav.hjelpemidler.joark.publish
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService

private val log = KotlinLogging.logger {}

class NotatOpprettetOpprettOgFerdigstillJournalpost(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("eventName", "hm-journalført-notat-opprettet") }
                validate {
                    it.requireKey(
                        "sakId",
                        "fnrSaksbehandler",
                        "fnrBruker",
                        "fysiskDokument",
                        "dokumenttittel",
                        "språkkode",
                    )
                    it.interestedIn("notatId", "opprettetAv")
                }
            }
            .register(this)
    }

    private val JsonMessage.sakId: String
        get() = this["sakId"].textValue()

    private val JsonMessage.fnrSaksbehandler: String
        get() = this["fnrSaksbehandler"].textValue()

    private val JsonMessage.fnrBruker: String
        get() = this["fnrBruker"].textValue()

    private val JsonMessage.fysiskDokument: ByteArray
        get() = this["fysiskDokument"].binaryValue()

    private val JsonMessage.dokumenttittel: String
        get() = this["dokumenttittel"].textValue()

    private val JsonMessage.språkkode: Språkkode
        get() = this["språkkode"].textValue().let(Språkkode::valueOf)

    private val JsonMessage.notatId: String?
        get() = this["notatId"].textValue()

    private val JsonMessage.opprettetAv: String?
        get() = this["opprettetAv"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val sakId = packet.sakId
        val fnrSaksbehandler = packet.fnrSaksbehandler
        val fnrBruker = packet.fnrBruker
        val dokumenttittel = packet.dokumenttittel
        val notatId = packet.notatId
        val opprettetAv = packet.opprettetAv

        log.info { "Mottok melding om at journalført notat er opprettet, sakId: $sakId, dokumenttype: ${Dokumenttype.NOTAT}, notatId: $notatId" }

        val fysiskDokument = packet.fysiskDokument

        val journalpostId = journalpostService.opprettJournalførtNotatJournalpost(
            fnrSaksbehandler = fnrSaksbehandler,
            fnrBruker = fnrBruker,
            eksternReferanseId = "${sakId}_${notatId}",
        ) {
            tittel = dokumenttittel
            dokument(fysiskDokument, dokumenttittel)
            hotsak(sakId)
            this.opprettetAv = opprettetAv
        }

        @Hendelse("hm-journalført-notat-journalført")
        data class JournalførtNotatJournalførtHendelse(
            val journalpostId: String,
            val sakId: String,
            val fnrSaksbehandler: String,
            val fnrBruker: String,
            val dokumenttittel: String,
            val dokumenttype: Dokumenttype,
            val notatId: String?,
            val opprettetAv: String?,
        )

        context.publish(
            key = fnrBruker,
            message = JournalførtNotatJournalførtHendelse(
                journalpostId = journalpostId,
                sakId = sakId,
                fnrSaksbehandler = fnrSaksbehandler,
                fnrBruker = fnrBruker,
                dokumenttittel = dokumenttittel,
                dokumenttype = Dokumenttype.NOTAT,
                notatId = notatId,
                opprettetAv = opprettetAv,
            )
        )
    }
}