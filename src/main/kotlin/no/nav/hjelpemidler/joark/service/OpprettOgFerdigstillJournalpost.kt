package no.nav.hjelpemidler.joark.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.features.BadRequestException
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
import no.nav.hjelpemidler.joark.joark.JoarkClientV2
import no.nav.hjelpemidler.joark.metrics.Prometheus
import no.nav.hjelpemidler.joark.pdf.PdfClient
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class OpprettOgFerdigstillJournalpost(
    rapidsConnection: RapidsConnection,
    private val pdfClient: PdfClient,
    private val joarkClientV2: JoarkClientV2,
    private val eventName: String = "hm-opprettetOgFerdigstiltJournalpost"
) : River.PacketListener {

    companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    private fun soknadToJson(soknad: JsonNode): String = objectMapper.writeValueAsString(soknad)

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@eventName", "hm-sakOpprettet") }
            validate { it.requireKey("soknadId", "sakId", "fnrBruker", "navnBruker", "soknadJson") }
        }.register(this)
    }

    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.navnBruker get() = this["navnBruker"].textValue()

    private val JsonMessage.søknadId get() = this["soknadId"].textValue()
    private val JsonMessage.søknad get() = this["soknadJson"]
    private val JsonMessage.sakId get() = this["sakId"].textValue()

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logger.error(problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {

        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    val journalpostData = JournalpostData(
                        fnrBruker = packet.fnrBruker,
                        navnBruker = packet.navnBruker,
                        soknadJson = soknadToJson(packet.søknad),
                        soknadId = UUID.fromString(packet.søknadId),
                        sakId = packet.sakId
                    )
                    logger.info { "Sak til journalføring mottatt: ${journalpostData.soknadId}" }
                    val pdf = genererPdf(journalpostData.soknadJson, journalpostData.soknadId)
                    try {
                        val journalpostResponse = opprettOgFerdigstillJournalpost(
                            journalpostData.fnrBruker,
                            journalpostData.navnBruker,
                            journalpostData.soknadId,
                            pdf,
                            journalpostData.sakId
                        )
                        forward(journalpostData, journalpostResponse.journalpostNr, context)
                    } catch (e: Exception) {
                        // Forsøk på arkivering av dokument med lik eksternReferanseId vil feile med 409 frå Joark/Dokarkiv si side
                        // Dette skjer kun dersom vi har arkivert søknaden tidlegare (prosessering av samme melding fleire gongar)
                        if (e.message != null && e.message!!.contains("409 Conflict")) return@launch
                        throw e
                    }
                }
            }
        }
    }

    private suspend fun genererPdf(soknadJson: String, soknadId: UUID) =
        kotlin.runCatching {
            pdfClient.genererPdf(soknadJson)
        }.onSuccess {
            logger.info("PDF generert: $soknadId")
            Prometheus.pdfGenerertCounter.inc()
        }.onFailure {
            logger.error(it) { "Feilet under generering av PDF: $soknadId" }
        }.getOrThrow()

    private suspend fun opprettOgFerdigstillJournalpost(
        fnrBruker: String,
        navnAvsender: String,
        soknadId: UUID,
        soknadPdf: ByteArray,
        sakId: String
    ) =
        kotlin.runCatching {
            joarkClientV2.opprettOgFerdigstillJournalføring(fnrBruker, navnAvsender, soknadId, soknadPdf, sakId)
        }.onSuccess {
            val journalpostnr = it.journalpostNr
            if (it.ferdigstilt) {
                logger.info("Opprettet og ferdigstilte journalpost i joark, journalpostNr: $journalpostnr")
            } else {
                logger.warn("Opprettet journalpost i joark, søknadId: $soknadId og journalpostNr: $journalpostnr, men klarte ikke å ferdigstille")
                throw BadRequestException("Klarte ikke å ferdigstille journalpost")
            }
            Prometheus.opprettettOgferdigstiltJournalpostCounter.inc()
        }.onFailure {
            logger.error(it) { "Feilet under opprettelse og ferdigstillelse journalpost for søknad: $soknadId" }
            throw it
        }.getOrThrow()

    private fun CoroutineScope.forward(journalpostData: JournalpostData, joarkRef: String, context: MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.publish(journalpostData.fnrBruker, journalpostData.toJson(joarkRef, eventName))
            Prometheus.soknadArkivertCounter.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    logger.info("Opprettet og ferdigstilte journalpost i joark for: ${journalpostData.soknadId}")
                    sikkerlogg.info("Opprettet og ferdigstilte journalpost for søknadsId: ${journalpostData.soknadId}, fnr: ${journalpostData.fnrBruker})")
                }
                is CancellationException -> logger.warn("Cancelled: ${it.message}")
                else -> {
                    logger.error("Failed: ${it.message}. Soknad: ${journalpostData.soknadId}")
                }
            }
        }
    }
}

internal data class JournalpostData(
    val fnrBruker: String,
    val navnBruker: String,
    val soknadId: UUID,
    val soknadJson: String,
    val sakId: String,
) {
    internal fun toJson(joarkRef: String, eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["soknadId"] = this.soknadId
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["fodselNrBruker"] = this.fnrBruker // @deprecated
            it["fnrBruker"] = this.fnrBruker
            it["joarkRef"] = joarkRef
            it["sakId"] = sakId
            it["eventId"] = UUID.randomUUID()
        }.toJson()
    }
}
