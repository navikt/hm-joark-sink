package no.nav.hjelpemidler.soknad.mottak.service

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
import no.nav.hjelpemidler.soknad.mottak.db.SoknadStore
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class SoknadDataSink(rapidsConnection: RapidsConnection, private val store: SoknadStore) :
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
            validate { it.forbid("@soknadId") }
            validate { it.requireKey("fodselNrBruker", "fodselNrInnsender", "soknad") }
        }.register(this)
    }

    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.fnrInnsender get() = this["fodselNrInnsender"].textValue()
    private val JsonMessage.soknadId get() = this["soknad"]["soknad"]["id"].textValue()
    private val JsonMessage.soknad get() = this["soknad"]
    private val JsonMessage.navnBruker get() = this["soknad"]["soknad"]["bruker"]["etternavn"].textValue() + " " + this["soknad"]["soknad"]["bruker"]["fornavn"].textValue()

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    val soknadData = SoknadData(
                        fnrBruker = packet.fnrBruker,
                        fnrInnsender = packet.fnrInnsender,
                        navnBruker = packet.navnBruker,
                        soknadJson = soknadToJson(packet.soknad),
                        soknad = packet.soknad,
                        soknadId = UUID.fromString(packet.soknadId)
                    )

                    logger.info { "Søknad mottatt: ${soknadData.soknadId}" }
                    save(soknadData)
                    forward(soknadData, context)
                }
            }
        }
    }

    private fun save(soknadData: SoknadData) =
        kotlin.runCatching {
            store.save(soknadData)
        }.onSuccess {
            logger.info("Søknad saved: ${soknadData.soknadId}")
            Prometheus.soknadCounter.inc()
        }.onFailure {
            logger.error(it) { "Failed to save søknad: ${soknadData.soknadId}" }
        }.getOrThrow()

    private fun CoroutineScope.forward(søknadData: SoknadData, context: RapidsConnection.MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.send(søknadData.fnrBruker, søknadData.toJson())
            Prometheus.soknadSendtCounter.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    logger.info("Søknad sent: ${søknadData.soknadId}")
                    sikkerlogg.info("Søknad sent med søknadsId: ${søknadData.soknadId}, fnr: ${søknadData.fnrBruker})")
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
    val fnrInnsender: String,
    val navnBruker: String,
    val soknadId: UUID,
    val soknadJson: String,
    val soknad: JsonNode
) {
    internal fun toJson(): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["@soknadId"] = this.soknadId
            it["@event_name"] = "Søknad"
            it["@opprettet"] = LocalDateTime.now()
            it["fnrBruker"] = this.fnrBruker
            it["navnBruker"] = this.navnBruker
            it["soknad"] = this.soknad
        }.toJson()
    }
}
