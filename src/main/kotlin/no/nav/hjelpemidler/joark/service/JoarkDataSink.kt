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
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.joark.Configuration
import no.nav.hjelpemidler.joark.joark.JoarkClient
import no.nav.hjelpemidler.joark.metrics.Prometheus
import no.nav.hjelpemidler.joark.pdf.PdfClient
import no.nav.hjelpemidler.joark.service.hotsak.Sakstype
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class JoarkDataSink(
    rapidsConnection: RapidsConnection,
    private val pdfClient: PdfClient,
    private val joarkClient: JoarkClient,
    private val eventName: String = Configuration.EVENT_NAME,
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
            validate {
                it.demandAny(
                    "eventName",
                    listOf("hm-Søknad", "hm-SøknadGodkjentAvBruker", "hm-søknadFordeltGammelFlyt")
                )
            }
            validate { it.requireKey("fodselNrBruker", "navnBruker", "soknad", "soknadId") }
            validate { it.interestedIn("soknadGjelder") }
        }.register(this)
    }

    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.navnBruker get() = this["navnBruker"].textValue()
    private val JsonMessage.soknadId get() = this["soknadId"].textValue()
    private val JsonMessage.soknad get() = this["soknad"]
    private val JsonMessage.soknadGjelder get() = this["soknadGjelder"].textValue() ?: "Søknad om hjelpemidler"

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    val soknadData = SoknadData(
                        fnrBruker = packet.fnrBruker,
                        navnBruker = packet.navnBruker,
                        soknadJson = soknadToJson(packet.soknad),
                        soknadId = UUID.fromString(packet.soknadId),
                        soknadGjelder = packet.runCatching {
                            packet.soknadGjelder
                        }.getOrDefault("Søknad om hjelpemidler")
                    )

                    if (soknadData.soknadId in listOf<UUID>(
                            UUID.fromString("7c020fe0-cbe3-4bd2-81c6-ab62dadf44f6"),
                            UUID.fromString("16565b25-1d9a-4dbb-b62e-8c68cc6a64c8"),
                            UUID.fromString("ddfd0e1e-a493-4395-9a63-783a9c1fadf0"),
                            UUID.fromString("99103106-dd24-4368-bf97-672f0b590ee3")
                        )

                    ) {
                        return@launch
                    }

                    logger.info { "Søknad til arkivering mottatt: ${soknadData.soknadId} med dokumenttittel ${soknadData.soknadGjelder}" }
                    val pdf = genererPdf(soknadData.soknadJson, soknadData.soknadId)
                    try {
                        val joarkRef = arkiver(
                            soknadData.fnrBruker,
                            soknadData.navnBruker,
                            soknadData.soknadGjelder,
                            soknadData.soknadId,
                            pdf
                        )
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
            pdfClient.genererSøknadPdf(soknadJson)
        }.onSuccess {
            logger.info("PDF generert: $soknadId")
            Prometheus.pdfGenerertCounter.inc()
        }.onFailure {
            logger.error(it) { "Feilet under generering av PDF: $soknadId" }
        }.getOrThrow()

    private suspend fun arkiver(
        fnrBruker: String,
        navnAvsender: String,
        dokumentTittel: String,
        soknadId: UUID,
        soknadPdf: ByteArray,
    ) =
        kotlin.runCatching {
            joarkClient.arkiverSoknad(
                fnrBruker,
                navnAvsender,
                dokumentTittel,
                soknadId,
                soknadPdf,
                Sakstype.SØKNAD
            )
        }.onSuccess {
            logger.info("Søknad arkivert: $soknadId")
            Prometheus.pdfGenerertCounter.inc()
        }.onFailure {
            logger.error(it) { "Feilet under arkivering av søknad: $soknadId" }
        }.getOrThrow()

    private fun CoroutineScope.forward(søknadData: SoknadData, joarkRef: String, context: MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.publish(søknadData.fnrBruker, søknadData.toJson(joarkRef, eventName))
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
    val soknadGjelder: String,
) {
    internal fun toJson(joarkRef: String, eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["soknadId"] = this.soknadId
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["fodselNrBruker"] = this.fnrBruker // @deprecated
            it["fnrBruker"] = this.fnrBruker
            it["joarkRef"] = joarkRef
            it["eventId"] = UUID.randomUUID()
            it["soknadGjelder"] = soknadGjelder
        }.toJson()
    }
}
