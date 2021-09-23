package no.nav.hjelpemidler.joark.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.joark.joark.JoarkClientV2
import no.nav.hjelpemidler.joark.metrics.Prometheus
import no.nav.hjelpemidler.joark.pdf.PdfClient
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class OpprettMidlertidigJournalpost(
    rapidsConnection: RapidsConnection,
    private val pdfClient: PdfClient,
    private val joarkClientV2: JoarkClientV2,
    private val eventName: String = "hm-SøknadArkivertMidlertidig-NyFlyt"
) : PacketListenerWithOnError {

    companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    private fun soknadToJson(soknad: JsonNode): String = objectMapper.writeValueAsString(soknad)

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "søknad_fordelt_ny_flyt") }
            validate { it.requireKey("søknadId", "saksgrunnlag") }
        }.register(this)
    }

    private val JsonMessage.fnrBruker get() = this["saksgrunnlag"]["person"]["fødselsnummer"].textValue()
    private val JsonMessage.fornavn get() = this["saksgrunnlag"]["person"]["fornavn"].textValue()
    private val JsonMessage.mellomnavn get() = this["saksgrunnlag"]["person"]["mellomnavn"]?.textValue()
    private val JsonMessage.etternavn get() = this["saksgrunnlag"]["person"]["etternavn"].textValue()

    private val JsonMessage.søknadId get() = this["søknadId"].textValue()
    private val JsonMessage.søknad get() = this["saksgrunnlag"]["søknad"]["data"]

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        if (System.getenv("NAIS_CLUSTER_NAME") != null && System.getenv("NAIS_CLUSTER_NAME") == "prod-gcp") {
            return
        }

        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    val soknadData = SoknadData(
                        fnrBruker = packet.fnrBruker,
                        navnBruker = "${packet.fornavn} ${packet.mellomnavn?.let { "${packet.mellomnavn} ${packet.etternavn}" } ?: "${packet.etternavn}"}",
                        soknadJson = soknadToJson(packet.søknad),
                        soknadId = UUID.fromString(packet.søknadId)
                    )
                    logger.info { "Søknad til midlertidig journalføring mottatt: ${soknadData.soknadId}" }
                    val pdf = genererPdf(soknadData.soknadJson, soknadData.soknadId)
                    try {
                        val joarkRef = opprettMidlertidigJournalpost(soknadData.fnrBruker, soknadData.navnBruker, soknadData.soknadId, pdf)
                        forward(soknadData, joarkRef, context)
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

    private suspend fun opprettMidlertidigJournalpost(fnrBruker: String, navnAvsender: String, soknadId: UUID, soknadPdf: ByteArray) =
        kotlin.runCatching {
            joarkClientV2.opprettMidlertidigJournalføring(fnrBruker, navnAvsender, soknadId, soknadPdf)
        }.onSuccess {
            logger.info("Opprettet midlertidig journalpost i joark: $soknadId")
            Prometheus.midlertidigJournalpostCounter.inc()
        }.onFailure {
            logger.error(it) { "Feilet under opprettelse av midlertidig journalpost for søknad: $soknadId" }
        }.getOrThrow()

    private fun CoroutineScope.forward(søknadData: SoknadData, joarkRef: String, context: MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.publish(søknadData.fnrBruker, søknadData.toJson(joarkRef, eventName))
            Prometheus.soknadArkivertCounter.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    logger.info("Opprettet midlertidig journalpost i joark for: ${søknadData.soknadId}")
                    sikkerlogg.info("Opprettet midlertidig journalpost for søknadsId: ${søknadData.soknadId}, fnr: ${søknadData.fnrBruker})")
                }
                is CancellationException -> logger.warn("Cancelled: ${it.message}")
                else -> {
                    logger.error("Failed: ${it.message}. Soknad: ${søknadData.soknadId}")
                }
            }
        }
    }
}
