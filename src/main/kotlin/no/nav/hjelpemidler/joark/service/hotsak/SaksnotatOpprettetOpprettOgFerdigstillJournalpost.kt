package no.nav.hjelpemidler.joark.service.hotsak

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.collections.joinToString
import no.nav.hjelpemidler.configuration.HotsakApplicationId
import no.nav.hjelpemidler.domain.id.EksternId
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
                        "saksnotatId",
                        "fnrBruker",
                        "dokumenttittel",
                        "fysiskDokument",
                        "strukturertDokument",
                        "opprettetAv",
                    )
                }
            }
            .register(this)
    }

    private val JsonMessage.sakId: String
        get() = this["sakId"].textValue()

    private val JsonMessage.saksnotatId: String
        get() = this["saksnotatId"].textValue()

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
        val fnrBruker = packet.fnrBruker
        val dokumenttittel = packet.dokumenttittel
        val opprettetAv = packet.opprettetAv

        log.info {
            mapOf(
                "sakId" to sakId,
                "saksnotatId" to saksnotatId,
                "dokumenttype" to Dokumenttype.NOTAT,
                "inkludererStrukturertDokument" to (packet.strukturertDokument != null)
            ).joinToString(prefix = "Mottok melding om at saksnotat er opprettet, ")
        }

        val fysiskDokument = packet.fysiskDokument
        val strukturertDokument = packet.strukturertDokument

        val journalpost = journalpostService.opprettNotat(
            fnrBruker = fnrBruker,
            eksternReferanseId = EksternId(
                applicationId = HotsakApplicationId,
                resource = "saksnotat",
                id = saksnotatId,
            ),
        ) {
            this.tittel = dokumenttittel
            this.opprettetAv = opprettetAv
            dokument(
                fysiskDokument = fysiskDokument,
                strukturertDokument = strukturertDokument,
                dokumenttittel = dokumenttittel,
            )
            hotsak(sakId)
            tilleggsopplysninger(
                "sakId" to sakId,
                "saksnotatId" to saksnotatId,
                prefix = HotsakApplicationId.application,
            )
        }

        context.publish(
            key = fnrBruker,
            message = JournalførtNotatJournalførtHendelse(
                journalpostId = journalpost.journalpostId,
                dokumentId = journalpost.dokumentIder.single(),
                sakId = sakId,
                saksnotatId = saksnotatId,
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
    val saksnotatId: String?,
    val fnrBruker: String,
    val dokumenttittel: String,
    val dokumenttype: Dokumenttype,
    val opprettetAv: String,
)
