package no.nav.hjelpemidler.joark.service.hotsak

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.Hendelse
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.publish
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService

private val log = KotlinLogging.logger {}

class SaksnotatOpprettetOpprettOgFerdigstillJournalpost(
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
                        "fnrBruker",
                        "dokumenttittel",
                        "fysiskDokument",
                        "strukturertDokument",
                        "opprettetAv",
                    )
                    it.interestedIn("saksnotatId", "brevsendingId") // todo -> brevsendingId fjernes
                }
            }
            .register(this)
    }

    private val JsonMessage.sakId: String
        get() = this["sakId"].textValue()

    private val JsonMessage.saksnotatId: String? // todo -> ikke nullable
        get() = this["saksnotatId"].textValue()

    private val JsonMessage.brevsendingId: String? // todo -> fjernes
        get() = this["brevsendingId"].textValue()

    private val JsonMessage.fnrBruker: String
        get() = this["fnrBruker"].textValue()

    private val JsonMessage.dokumenttittel: String
        get() = this["dokumenttittel"].textValue()

    private val JsonMessage.fysiskDokument: ByteArray
        get() = this["fysiskDokument"].binaryValue()

    private val JsonMessage.strukturertDokument: JsonNode?
        get() = this["strukturertDokument"].let { if (it.isNull) null else it }

    private val JsonMessage.opprettetAv: String
        get() = this["opprettetAv"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val sakId = packet.sakId
        val saksnotatId = packet.saksnotatId
        val brevsendingId = packet.brevsendingId
        val fnrBruker = packet.fnrBruker
        val dokumenttittel = packet.dokumenttittel
        val opprettetAv = packet.opprettetAv

        log.info { "Mottok melding om at saksnotat er opprettet, sakId: $sakId, saksnotatId: $saksnotatId, dokumenttype: ${Dokumenttype.NOTAT}, brevsendingId: $brevsendingId, inkludererStrukturertDokument: ${packet.strukturertDokument != null}" }

        val fysiskDokument = packet.fysiskDokument
        val strukturertDokument = packet.strukturertDokument

        val journalpost = journalpostService.opprettNotat(
            fnrBruker = fnrBruker,
            eksternReferanseId = if (saksnotatId == null) "hotsak-jfrnotat-${sakId}_${brevsendingId}" else "hotsak-saksnotat-${sakId}_${saksnotatId}",
        ) {
            tittel = dokumenttittel
            dokument(
                fysiskDokument = fysiskDokument,
                strukturertDokument = strukturertDokument,
                dokumenttittel = dokumenttittel,
            )
            hotsak(sakId)
            this.opprettetAv = opprettetAv
        }

        context.publish(
            key = fnrBruker,
            message = JournalførtNotatJournalførtHendelse(
                journalpostId = journalpost.journalpostId,
                dokumentId = journalpost.dokumentIder.single(),
                sakId = sakId,
                saksnotatId = saksnotatId,
                brevsendingId = brevsendingId,
                fnrBruker = fnrBruker,
                dokumenttittel = dokumenttittel,
                dokumenttype = Dokumenttype.NOTAT,
                opprettetAv = opprettetAv,
            )
        )
    }
}

@Hendelse("hm-journalført-notat-journalført")
private data class JournalførtNotatJournalførtHendelse(
    val journalpostId: String,
    val dokumentId: String,
    val sakId: String,
    val saksnotatId: String?, // todo -> ikke nullable
    @Deprecated("Byttes med saksnotatId")
    val brevsendingId: String?,
    val fnrBruker: String,
    val dokumenttittel: String,
    val dokumenttype: Dokumenttype,
    val opprettetAv: String,
)
