package no.nav.hjelpemidler.joark.service.hotsak

import com.fasterxml.jackson.databind.JsonNode
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.joark.domain.Sakstype
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
        val data = MottattJournalpostData(
            fnrBruker = packet.fnrBruker,
            navnBruker = packet.navnBruker,
            soknadJson = packet.søknadJson,
            soknadId = packet.søknadId,
            sakId = sakId,
            sakstype = packet.sakstype,
            dokumentBeskrivelse = packet.dokumentBeskrivelse,
            enhet = packet.enhet,
            navIdent = packet.navIdent,
            valgteÅrsaker = packet.valgteÅrsaker,
            begrunnelse = packet.begrunnelse,
        )
        log.info { "Sak til journalføring etter feilregistrering mottatt, sakId: $sakId, søknadId: ${data.soknadId}, sakstype: ${data.sakstype}, dokumenttittel: ${data.dokumentBeskrivelse}" }
        val eksternReferanseId = "${data.soknadId}_${packet.journalpostId}_HOTSAK_TIL_GOSYS"

        try {
            val nyJournalpostId = when (data.sakstype) {
                Sakstype.BESTILLING, Sakstype.SØKNAD -> journalpostService.arkiverBehovsmelding(
                    fnrBruker = data.fnrBruker,
                    behovsmeldingId = data.soknadId,
                    sakstype = data.sakstype,
                    dokumenttittel = data.dokumentBeskrivelse,
                    eksternReferanseId = eksternReferanseId,
                )

                Sakstype.BARNEBRILLER -> journalpostService.kopierJournalpost(
                    journalpostId = packet.journalpostId,
                    nyEksternReferanseId = eksternReferanseId
                )

                Sakstype.BYTTE, Sakstype.BRUKERPASSBYTTE -> throw IllegalArgumentException("Uventet sakstype ${data.sakstype}")
            }

            context.publish(data.fnrBruker, data.toJson(nyJournalpostId, "hm-opprettetMottattJournalpost"))
            log.info { "Opprettet journalpost med status mottatt i dokarkiv for søknadId: ${data.soknadId}, journalpostId: $nyJournalpostId, sakstype: ${data.sakstype}" }
        } catch (e: Throwable) {
            log.error(e) { "Klarte ikke å opprette journalpost med status mottatt i dokarkiv for søknadId: ${data.soknadId}, sakstype: ${data.sakstype}" }
            throw e
        }
    }
}

private fun skip(sakId: String): Boolean =
    sakId in setOf("2295", "158062")

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
        return JsonMessage("{}", MessageProblems("")).also {
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
