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
import no.nav.helse.rapids_rivers.asLocalDate
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
                    "soknadJson",
                    "soknadId",
                    "mottattDato"
                )
                it.interestedIn(
                    "sakstype",
                    "navIdent",
                    "valgteÅrsaker"
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
    private val JsonMessage.søknadJson get() = this["soknadJson"]
    private val JsonMessage.mottattDato get() = this["mottattDato"].asLocalDate()
    private val JsonMessage.sakstype get() = this["sakstype"].textValue()
    private val JsonMessage.navIdent get() = this["navIdent"].textValue()
    private val JsonMessage.valgteÅrsaker: Set<String> get() = this["valgteÅrsaker"].map { it.textValue() }.toSet()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val journalpostId = packet.journalpostId
        if (skip(journalpostId)) {
            log.warn {
                "Hopper over feilregistrering av journalpost med journalpostId: $journalpostId"
            }
            return
        }
        coroutineScope {
            launch {
                val data = FeilregistrerJournalpostData(
                    sakId = packet.sakId,
                    sakstype = packet.sakstype,
                    journalpostId = journalpostId,
                    fnrBruker = packet.fnrBruker,
                    navnBruker = packet.navnBruker,
                    dokumentBeskrivelse = packet.dokumentBeskrivelse,
                    soknadId = packet.søknadId,
                    soknadJson = packet.søknadJson,
                    mottattDato = packet.mottattDato,
                    navIdent = packet.navIdent,
                    valgteÅrsaker = packet.valgteÅrsaker,
                )
                log.info {
                    "Journalpost til feilregistrering av sak mottatt (${data.sakstype}), sakId: ${data.sakId}, journalpostId: $journalpostId"
                }
                journalpostService.feilregistrerSakstilknytning(journalpostId)
                forward(journalpostId, data, context)
            }
        }
    }

    private fun CoroutineScope.forward(
        journalpostId: String,
        data: FeilregistrerJournalpostData,
        context: MessageContext,
    ) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.publish(
                data.fnrBruker,
                data.toJson(journalpostId, "hm-feilregistrerteSakstilknytningForJournalpost")
            )
        }.invokeOnCompletion {
            val sakId = data.sakId
            when (it) {
                null -> {
                    log.info {
                        "Feilregistrerte sakstilknytning for sakId: $sakId, journalpostId: $journalpostId"
                    }
                }

                is CancellationException -> log.warn(it) {
                    "Cancelled"
                }

                else -> {
                    log.error(it) {
                        "Klarte ikke å feilregistrere sakstilknytning for journalpostId: $journalpostId, sakId: $sakId"
                    }
                }
            }
        }
    }
}

private fun skip(journalpostId: String) =
    journalpostId in setOf("535250492")

private data class FeilregistrerJournalpostData(
    val sakId: String,
    val sakstype: String,
    val journalpostId: String,
    val fnrBruker: String,
    val navnBruker: String,
    val dokumentBeskrivelse: String,
    val soknadId: String,
    val soknadJson: JsonNode,
    val mottattDato: LocalDate,
    val navIdent: String?,
    val valgteÅrsaker: Set<String>,
) {
    @Deprecated("Bruk Jackson direkte")
    fun toJson(nyJournalpostId: String, eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
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
            it["soknadId"] = this.soknadId
            it["soknadJson"] = this.soknadJson
            it["mottattDato"] = this.mottattDato
            if (this.navIdent != null) {
                it["navIdent"] = this.navIdent
            }
            it["valgteÅrsaker"] = this.valgteÅrsaker
        }.toJson()
    }
}
