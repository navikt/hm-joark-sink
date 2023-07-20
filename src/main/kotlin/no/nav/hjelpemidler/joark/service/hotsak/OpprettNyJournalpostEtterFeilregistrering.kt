package no.nav.hjelpemidler.joark.service.hotsak

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.domain.Sakstype
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}
private val secureLog = KotlinLogging.logger("tjenestekall")

class OpprettNyJournalpostEtterFeilregistrering(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-feilregistrerteSakstilknytningForJournalpost") }
            validate { it.requireKey("soknadId", "sakId", "fnrBruker", "navnBruker", "soknadJson", "mottattDato") }
            validate {
                it.interestedIn("dokumentBeskrivelse", "sakstype", "nyJournalpostId", "navIdent", "valgteÅrsaker", "enhet", "begrunnelse")
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
        coroutineScope {
            launch {
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
                log.info { "Sak til journalføring etter feilregistrering mottatt, søknadId: ${data.soknadId}, sakstype: ${data.sakstype}, dokumenttittel: ${data.dokumentBeskrivelse}" }
                val eksternReferanseId = "${data.soknadId}HOTSAK_TIL_GOSYS"
                val nyJournalpostId = when (data.sakstype) {
                    Sakstype.BESTILLING, Sakstype.SØKNAD -> journalpostService.arkiverSøknad(
                        fnrBruker = data.fnrBruker,
                        søknadId = data.soknadId,
                        søknadJson = packet.søknadJson,
                        sakstype = data.sakstype,
                        dokumenttittel = data.dokumentBeskrivelse,
                        eksternReferanseId = eksternReferanseId,
                    )

                    Sakstype.BARNEBRILLER -> journalpostService.kopierJournalpost(
                        journalpostId = packet.journalpostId,
                        nyEksternReferanseId = eksternReferanseId
                    )
                }
                forward(nyJournalpostId, data, context)
            }
        }
    }

    private fun CoroutineScope.forward(
        journalpostId: String,
        data: MottattJournalpostData,
        context: MessageContext,
    ) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.publish(
                data.fnrBruker,
                data.toJson(journalpostId, "hm-opprettetMottattJournalpost")
            )
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    log.info {
                        "Opprettet journalpost med status mottatt i joark for søknadId: ${data.soknadId}"
                    }
                    secureLog.info {
                        "Opprettet journalpost med status mottatt i joark for søknadId: ${data.soknadId}, fnr: ${data.fnrBruker}"
                    }
                }

                is CancellationException -> log.warn(it) {
                    "Cancelled"
                }

                else -> log.error(it) {
                    "Klarte ikke å opprette journalpost med status mottatt i joark for søknadId: ${data.soknadId}"
                }
            }
        }
    }
}

private fun skip(sakId: String): Boolean =
    sakId in setOf("2295")

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
