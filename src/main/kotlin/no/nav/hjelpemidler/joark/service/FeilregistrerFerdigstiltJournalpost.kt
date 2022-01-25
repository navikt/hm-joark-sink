package no.nav.hjelpemidler.joark.service

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.joark.Configuration
import no.nav.hjelpemidler.joark.Profile
import no.nav.hjelpemidler.joark.joark.JoarkClientV2
import no.nav.hjelpemidler.joark.metrics.Prometheus
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal class FeilregistrerFerdigstiltJournalpost(
    rapidsConnection: RapidsConnection,
    private val joarkClientV2: JoarkClientV2,
    private val eventName: String = "hm-sakTilbakeførtGosys"
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", eventName) }
            validate {
                it.requireKey(
                    "saksnummer",
                    "joarkRef",
                    "navnBruker",
                    "fnrBruker",
                    "dokumentBeskrivelse",
                    "soknadJson",
                    "soknadId"
                )
            }
        }.register(this)
    }

    private val JsonMessage.sakId get() = this["saksnummer"].textValue()
    private val JsonMessage.journalpostId get() = this["joarkRef"].textValue()
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.navnBruker get() = this["navnBruker"].textValue()
    private val JsonMessage.dokumentBeskrivelse get() = this["dokumentBeskrivelse"].textValue()
    private val JsonMessage.soknadJson get() = this["soknadJson"]
    private val JsonMessage.soknadId get() = this["soknadId"].textValue()

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logger.error(problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {

        if (packet.journalpostId == "453656272" || packet.journalpostId == "453657158" || packet.journalpostId == "510871046") {
            return
        }

        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    val journalpostData = FeilregistrerJournalpostData(
                        sakId = packet.sakId,
                        journalpostId = packet.journalpostId,
                        fnrBruker = packet.fnrBruker,
                        navnBruker = packet.navnBruker,
                        dokumentBeskrivelse = packet.dokumentBeskrivelse,
                        soknadJson = packet.soknadJson,
                        soknadId = packet.soknadId
                    )
                    logger.info {
                        "Journalpost til feilregistrering av sak mottatt: ${journalpostData.sakId}, " +
                            "journalpostId: ${journalpostData.journalpostId}"
                    }
                    try {
                        val nyJournalpostId = feilregistrerJournalpost(
                            journalpostData.sakId,
                            journalpostData.journalpostId
                        )
                        forward(journalpostData, nyJournalpostId, context)
                    } catch (e: Exception) {
                        if (e.message != null && e.message!!.contains("409 Conflict")) return@launch
                        throw e
                    }
                }
            }
        }
    }

    private suspend fun feilregistrerJournalpost(
        sakId: String,
        journalpostId: String,
    ) =
        kotlin.runCatching {
            joarkClientV2.feilregistrerJournalpostData(journalpostId)
        }.onSuccess {
            logger.info(
                "Feilregistrerte sakstilknytning for journalpostNr: " +
                    "$journalpostId, sak: $sakId"
            )
            Prometheus.feilregistrerteSakstilknytningForJournalpostCounter.inc()
        }.onFailure {
            logger.error(it) { "Feilet under feilregistrering av sakstilknytning for journalpost: $$journalpostId, sak: $sakId" }
            throw it
        }.getOrThrow()

    private fun CoroutineScope.forward(
        journalpostData: FeilregistrerJournalpostData,
        nyJournalpostId: String,
        context: MessageContext
    ) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.publish(
                journalpostData.fnrBruker,
                journalpostData.toJson(nyJournalpostId, "hm-feilregistrerteSakstilknytningForJournalpost")
            )
            Prometheus.soknadArkivertCounter.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    logger.info(
                        "Feilregistrerte sakstilknytning for sakId: " +
                            "${journalpostData.sakId}, journalpostNr: ${journalpostData.journalpostId}"
                    )
                }
                is CancellationException -> logger.warn("Cancelled: ${it.message}")
                else -> {
                    logger.error(
                        "Klarte ikke å feilregistrere sakstilknytning for journalpost: ${journalpostData.journalpostId}" +
                            " sak: ${journalpostData.sakId}" +
                            " Feil: ${it.message}"
                    )
                }
            }
        }
    }
}

internal data class FeilregistrerJournalpostData(
    val sakId: String,
    val journalpostId: String,
    val fnrBruker: String,
    val navnBruker: String,
    val dokumentBeskrivelse: String,
    val soknadJson: JsonNode,
    val soknadId: String
) {
    internal fun toJson(nyJournalpostId: String, eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["sakId"] = sakId
            it["feilregistrertJournalpostId"] = journalpostId
            it["nyJournalpostId"] = nyJournalpostId
            it["eventId"] = UUID.randomUUID()
            it["fnrBruker"] = this.fnrBruker
            it["navnBruker"] = this.navnBruker
            it["dokumentBeskrivelse"] = this.dokumentBeskrivelse
            it["soknadJson"] = this.soknadJson
            it["soknadId"] = this.soknadId
        }.toJson()
    }
}
