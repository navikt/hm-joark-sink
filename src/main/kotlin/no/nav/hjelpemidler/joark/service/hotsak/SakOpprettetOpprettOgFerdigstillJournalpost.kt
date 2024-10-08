package no.nav.hjelpemidler.joark.service.hotsak

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.domain.Sakstype
import no.nav.hjelpemidler.joark.jsonMessage
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Automatisk journalføring av saker fra Hotsak.
 */
class SakOpprettetOpprettOgFerdigstillJournalpost(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-sakOpprettet") }
            validate { it.requireKey("soknadId", "sakId", "fnrBruker", "navnBruker", "soknadJson", "soknadGjelder") }
        }.register(this)
    }

    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.navnBruker get() = this["navnBruker"].textValue()

    private val JsonMessage.søknadId get() = this["soknadId"].textValue()
    private val JsonMessage.søknadJson get() = this["soknadJson"]
    private val JsonMessage.sakId get() = this["sakId"].textValue()
    private val JsonMessage.søknadGjelder get() = this["soknadGjelder"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val data = JournalpostData(
            fnrBruker = packet.fnrBruker,
            navnBruker = packet.navnBruker,
            soknadJson = packet.søknadJson,
            soknadId = UUID.fromString(packet.søknadId),
            sakId = packet.sakId,
            dokumentTittel = packet.søknadGjelder
        )
        val dokumenttype = Sakstype.valueOf(packet.søknadJson.at("/behovsmeldingType").textValue()).dokumenttype
        log.info {
            "Sak til journalføring mottatt, søknadId: ${data.soknadId}, sakId: ${data.sakId}, dokumenttype: $dokumenttype, dokumenttittel: '${data.dokumentTittel}'"
        }

        try {
            val fysiskDokument = journalpostService.hentBehovsmeldingPdf(data.soknadId)
            val journalpostId = journalpostService.opprettInngåendeJournalpost(
                fnrAvsender = data.fnrBruker,
                dokumenttype = dokumenttype,
                eksternReferanseId = "${data.soknadId}HOTSAK",
                forsøkFerdigstill = true,
            ) {
                dokument(
                    fysiskDokument = fysiskDokument,
                    dokumenttittel = data.dokumentTittel
                )
                hotsak(data.sakId)
            }.journalpostId

            context.publish(data.fnrBruker, data.toJson(journalpostId, "hm-opprettetOgFerdigstiltJournalpost"))
            log.info("Opprettet og ferdigstilte journalpost i joark for søknadId: ${data.soknadId}")
        } catch (e: Throwable) {
            log.error(e) { "Klarte ikke å opprettet og ferdigstille journalpost i joark for søknadId: ${data.soknadId}" }
            throw e
        }
    }
}

private data class JournalpostData(
    val fnrBruker: String,
    val navnBruker: String,
    val soknadId: UUID,
    val soknadJson: JsonNode,
    val sakId: String,
    val dokumentTittel: String,
) {
    @Deprecated("Bruk Jackson direkte")
    fun toJson(journalpostId: String, eventName: String): String {
        return jsonMessage {
            it["soknadId"] = this.soknadId
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["fodselNrBruker"] = this.fnrBruker // @deprecated
            it["fnrBruker"] = this.fnrBruker
            it["joarkRef"] = journalpostId
            it["sakId"] = sakId
            it["dokumentTittel"] = dokumentTittel
            it["eventId"] = UUID.randomUUID()
        }.toJson()
    }
}
