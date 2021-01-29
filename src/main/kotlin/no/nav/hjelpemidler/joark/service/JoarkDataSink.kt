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
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.joark.metrics.Prometheus
import no.nav.hjelpemidler.joark.oppslag.PdfClient
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class JoarkDataSink(rapidsConnection: RapidsConnection, private val pdfClient: PdfClient) :
    River.PacketListener {

    companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    private fun soknadToJson(soknad: JsonNode): String = objectMapper.writeValueAsString(soknad)

    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("fodselNrBruker", "navnBruker", "soknad", "soknadId") }
        }.register(this)
    }

    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.navnBruker get() = this["navnBruker"].textValue()
    private val JsonMessage.soknadId get() = this["soknadId"].textValue()
    private val JsonMessage.soknad get() = this["soknad"]

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    val soknadData = SoknadData(
                        fnrBruker = packet.fnrBruker,
                        navnBruker = packet.navnBruker,
                        soknadJson = soknadToJson(packet.soknad),
                        soknadId = UUID.fromString(packet.soknadId)
                    )
                    logger.info { "Søknad til arkivering mottatt: ${soknadData.soknadId}" }
                    val pdf = genererPdf(soknadData)
                    forward(soknadData, context)
                }
            }
        }
    }

    private suspend fun genererPdf(soknadData: SoknadData) =
        kotlin.runCatching {

            pdfClient.genererPdf(soknadData.soknadJson)
        }.onSuccess {
            logger.info("PDF generert: ${soknadData.soknadId}")
            Prometheus.pdfGenerertCounter.inc()
        }.onFailure {
            logger.error(it) { "Feilet under gerering av PDF: ${soknadData.soknadId}" }
        }.getOrThrow()

    private fun CoroutineScope.forward(søknadData: SoknadData, context: RapidsConnection.MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.send(søknadData.fnrBruker, søknadData.toJson("aJoarkRef"))
            Prometheus.soknadArkivertCounter.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    logger.info("Søknad arkivert i JOARK: ${søknadData.soknadId}")
                    sikkerlogg.info("Søknad arkivert med søknadsId: ${søknadData.soknadId}, fnr: ${søknadData.fnrBruker})")
                }
                is CancellationException -> logger.warn("Cancelled: ${it.message}")
                else -> {
                    logger.error("Failed: ${it.message}. Soknad: ${søknadData.soknadId}")
                }
            }
        }
    }
}

internal data class SoknadData(
    val fnrBruker: String,
    val navnBruker: String,
    val soknadId: UUID,
    val soknadJson: String,
) {
    internal fun toJson(joarkRef: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["soknadId"] = this.soknadId
            it["@event_name"] = "SøknadArkivert"
            it["@opprettet"] = LocalDateTime.now()
            it["fnrBruker"] = this.fnrBruker
            it["joarkRef"] = joarkRef
        }.toJson()
    }
}
