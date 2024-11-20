package no.nav.hjelpemidler.joark.service.hotsak

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.jsonMessage
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

class SakTilbakeførtFeilregistrerJournalpost(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-sakTilbakeførtGosys") }
            validate {
                it.requireKey(
                    "saksnummer",
                    "joarkRef",
                    "navnBruker",
                    "fnrBruker",
                    "dokumentBeskrivelse",
                    "soknadId",
                    "mottattDato"
                )
                it.interestedIn(
                    "sakstype",
                    "navIdent",
                    "valgteÅrsaker",
                    "enhet",
                    "begrunnelse",
                    "soknadJson",
                    "prioritet",
                )
            }
        }.register(this)
    }

    private val JsonMessage.sakId get() = this["saksnummer"].textValue()
    private val JsonMessage.journalpostId get() = this["joarkRef"].textValue()
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.navnBruker get() = this["navnBruker"].textValue()
    private val JsonMessage.dokumentBeskrivelse get() = this["dokumentBeskrivelse"].textValue()
    private val JsonMessage.søknadId get() = this["soknadId"].textValue()

    @Deprecated("Vi skal slutte å sende dette feltet")
    private val JsonMessage.søknadJson get() = this["soknadJson"]
    private val JsonMessage.mottattDato get() = this["mottattDato"].asLocalDate()
    private val JsonMessage.sakstype get() = this["sakstype"].textValue()
    private val JsonMessage.navIdent get() = this["navIdent"].textValue()
    private val JsonMessage.valgteÅrsaker: Set<String> get() = this["valgteÅrsaker"].map { it.textValue() }.toSet()
    private val JsonMessage.enhet get() = this["enhet"].textValue()
    private val JsonMessage.begrunnelse get() = this["begrunnelse"].textValue()
    private val JsonMessage.prioritet: String? get() = this["prioritet"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val journalpostId = packet.journalpostId
        if (skip(journalpostId)) {
            log.warn {
                "Hopper over feilregistrering av journalpost med journalpostId: $journalpostId"
            }
            return
        }
        val data = FeilregistrerJournalpostData(
            sakId = packet.sakId,
            sakstype = packet.sakstype,
            journalpostId = journalpostId,
            fnrBruker = packet.fnrBruker,
            navnBruker = packet.navnBruker,
            dokumentBeskrivelse = packet.dokumentBeskrivelse,
            enhet = packet.enhet,
            soknadId = packet.søknadId,
            soknadJson = packet.søknadJson,
            mottattDato = packet.mottattDato,
            navIdent = packet.navIdent,
            valgteÅrsaker = packet.valgteÅrsaker,
            begrunnelse = packet.begrunnelse,
            prioritet = packet.prioritet,
        )
        log.info {
            "Journalpost til feilregistrering av sakstilknytning mottatt, sakId: ${data.sakId}, sakstype: ${data.sakstype}, journalpostId: $journalpostId"
        }

        try {
            journalpostService.feilregistrerSakstilknytning(journalpostId)

            context.publish(
                data.fnrBruker,
                data.toJson(journalpostId, "hm-feilregistrerteSakstilknytningForJournalpost")
            )
            log.info { "Feilregistrerte sakstilknytning for sakId: ${data.sakId}, journalpostId: $journalpostId" }
        } catch (e: Throwable) {
            log.error(e) { "Klarte ikke å feilregistrere sakstilknytning for journalpostId: $journalpostId, sakId: ${data.sakId}" }
            throw e
        }
    }
}

private fun skip(journalpostId: String): Boolean =
    journalpostId in setOf("535250492")

private data class FeilregistrerJournalpostData(
    val sakId: String,
    val sakstype: String,
    val journalpostId: String,
    val fnrBruker: String,
    val navnBruker: String,
    val dokumentBeskrivelse: String,
    val enhet: String,
    val soknadId: String,
    val soknadJson: JsonNode,
    val mottattDato: LocalDate,
    val navIdent: String?,
    val valgteÅrsaker: Set<String>,
    val begrunnelse: String?,
    val prioritet: String?,
) {
    @Deprecated("Bruk Jackson direkte")
    fun toJson(nyJournalpostId: String, eventName: String): String {
        return jsonMessage {
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["sakId"] = sakId
            it["sakstype"] = this.sakstype
            it["feilregistrertJournalpostId"] = journalpostId
            it["nyJournalpostId"] = nyJournalpostId
            it["eventId"] = UUID.randomUUID()
            it["fnrBruker"] = this.fnrBruker
            it["navnBruker"] = this.navnBruker
            it["dokumentBeskrivelse"] = this.dokumentBeskrivelse
            it["enhet"] = this.enhet
            it["soknadId"] = this.soknadId
            it["soknadJson"] = this.soknadJson
            it["mottattDato"] = this.mottattDato
            if (this.navIdent != null) {
                it["navIdent"] = this.navIdent
            }
            it["valgteÅrsaker"] = this.valgteÅrsaker
            if (this.begrunnelse != null) {
                it["begrunnelse"] = this.begrunnelse
            }
            if (this.prioritet != null) {
                it["prioritet"] = this.prioritet
            }
        }.toJson()
    }
}
