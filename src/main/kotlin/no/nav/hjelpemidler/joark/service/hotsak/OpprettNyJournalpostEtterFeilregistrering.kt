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

class OpprettNyJournalpostEtterFeilregistrering(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-feilregistrerteSakstilknytningForJournalpost") }
            validate { it.requireKey("soknadId", "sakId", "fnrBruker", "navnBruker", "soknadJson", "mottattDato") }
            validate {
                it.interestedIn(
                    "dokumentBeskrivelse",
                    "sakstype",
                    "nyJournalpostId",
                    "navIdent",
                    "valgteÅrsaker",
                    "enhet",
                    "begrunnelse"
                )
            }
        }.register(this)
    }

    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.navnBruker get() = this["navnBruker"].textValue()
    private val JsonMessage.søknadId get() = this["soknadId"].textValue().let(UUID::fromString)
    private val JsonMessage.søknadJson get() = this["soknadJson"]
    private val JsonMessage.sakId get() = this["sakId"].textValue()
    private val JsonMessage.dokumentBeskrivelse get() = this["dokumentBeskrivelse"].textValue()
    private val JsonMessage.sakstype get() = this["sakstype"].textValue().let(Sakstype::valueOf)
    private val JsonMessage.navIdent get() = this["navIdent"].textValue()
    private val JsonMessage.journalpostId get() = this["nyJournalpostId"].textValue()
    private val JsonMessage.valgteÅrsaker: Set<String> get() = this["valgteÅrsaker"].map { it.textValue() }.toSet()
    private val JsonMessage.enhet get() = this["enhet"].textValue()
    private val JsonMessage.begrunnelse get() = this["begrunnelse"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val sakId = packet.sakId
        if (skip(sakId)) {
            log.warn {
                "Hopper over opprettelse av ny journalpost for sakId: $sakId"
            }
            return
        }
        val søknadId = packet.søknadId
        val journalpostId = packet.journalpostId
        val data = MottattJournalpostData(
            fnrBruker = packet.fnrBruker,
            navnBruker = packet.navnBruker,
            soknadJson = packet.søknadJson,
            soknadId = søknadId,
            sakId = sakId,
            sakstype = packet.sakstype,
            dokumentBeskrivelse = packet.dokumentBeskrivelse,
            enhet = packet.enhet,
            navIdent = packet.navIdent,
            valgteÅrsaker = packet.valgteÅrsaker,
            begrunnelse = packet.begrunnelse,
        )
        log.info { "Sak til journalføring etter feilregistrering mottatt, sakId: $sakId, søknadId: $søknadId, sakstype: ${data.sakstype}, dokumenttittel: ${data.dokumentBeskrivelse}, journalpostId: $journalpostId" }
        val eksternReferanseId = "${søknadId}_${journalpostId}_HOTSAK_TIL_GOSYS"

        try {
            val nyJournalpostId = when (data.sakstype) {
                Sakstype.BESTILLING, Sakstype.SØKNAD -> journalpostService.arkiverBehovsmelding(
                    fnrBruker = data.fnrBruker,
                    behovsmeldingId = søknadId,
                    sakstype = data.sakstype,
                    dokumenttittel = data.dokumentBeskrivelse,
                    eksternReferanseId = eksternReferanseId,
                )

                Sakstype.BARNEBRILLER -> journalpostService.kopierJournalpost(
                    journalpostId = journalpostId,
                    nyEksternReferanseId = eksternReferanseId
                )

                Sakstype.BYTTE, Sakstype.BRUKERPASSBYTTE -> throw IllegalArgumentException("Uventet sakstype: ${data.sakstype}")
            }

            context.publish(data.fnrBruker, data.toJson(nyJournalpostId, "hm-opprettetMottattJournalpost"))
            log.info { "Opprettet journalpost med status mottatt i dokarkiv for søknadId: $søknadId, journalpostId: $nyJournalpostId, sakId: $sakId, sakstype: ${data.sakstype}" }
        } catch (e: Throwable) {
            log.error(e) { "Klarte ikke å opprette journalpost med status mottatt i dokarkiv for søknadId: $søknadId, sakstype: ${data.sakstype}" }
            throw e
        }
    }
}

private fun skip(sakId: String): Boolean =
    sakId in setOf("2295", "158062", "2944", "258980")

private data class MottattJournalpostData(
    val fnrBruker: String,
    val navnBruker: String,
    val soknadId: UUID,
    val soknadJson: JsonNode,
    val sakId: String,
    val sakstype: Sakstype,
    val dokumentBeskrivelse: String,
    val enhet: String,
    val navIdent: String?,
    val valgteÅrsaker: Set<String>,
    val begrunnelse: String?,
) {
    @Deprecated("Bruk Jackson direkte")
    fun toJson(journalpostId: String, eventName: String): String {
        return jsonMessage {
            it["soknadId"] = this.soknadId
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["fodselNrBruker"] = this.fnrBruker // @deprecated
            it["fnrBruker"] = this.fnrBruker
            it["joarkRef"] = journalpostId // @deprecated
            it["journalpostId"] = journalpostId
            it["sakId"] = sakId
            it["sakstype"] = this.sakstype
            it["dokumentBeskrivelse"] = this.dokumentBeskrivelse
            it["enhet"] = this.enhet
            it["eventId"] = UUID.randomUUID()
            it["soknadJson"] = this.soknadJson
            if (this.navIdent != null) {
                it["navIdent"] = this.navIdent
            }
            it["valgteÅrsaker"] = this.valgteÅrsaker
            if (this.begrunnelse != null) {
                it["begrunnelse"] = this.begrunnelse
            }
        }.toJson()
    }
}
