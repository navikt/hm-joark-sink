package no.nav.hjelpemidler.joark.service.hotsak

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
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.hjelpemidler.joark.joark.JoarkClient
import no.nav.hjelpemidler.joark.metrics.Prometheus
import no.nav.hjelpemidler.joark.pdf.PdfClient
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class OpprettMottattJournalpost(
    rapidsConnection: RapidsConnection,
    private val pdfClient: PdfClient,
    private val joarkClient: JoarkClient,
    private val eventName: String = "hm-opprettetMottattJournalpost"
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
            validate { it.demandValue("eventName", "hm-feilregistrerteSakstilknytningForJournalpost") }
            validate { it.requireKey("soknadId", "sakId", "fnrBruker", "navnBruker", "soknadJson", "mottattDato") }
            validate { it.interestedIn("dokumentBeskrivelse") }
        }.register(this)
    }

    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.navnBruker get() = this["navnBruker"].textValue()

    private val JsonMessage.søknadId get() = this["soknadId"].textValue()
    private val JsonMessage.soknadJson get() = this["soknadJson"]
    private val JsonMessage.sakId get() = this["sakId"].textValue()
    private val JsonMessage.dokumentBeskrivelse get() = this["dokumentBeskrivelse"].textValue()
    private val JsonMessage.mottattDato get() = this["mottattDato"].asLocalDate()

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logger.error(problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    val mottattJournalpostData = MottattJournalpostData(
                        fnrBruker = packet.fnrBruker,
                        navnBruker = packet.navnBruker,
                        soknadJson = packet.soknadJson,
                        soknadId = UUID.fromString(packet.søknadId),
                        sakId = packet.sakId,
                        dokumentBeskrivelse = packet.dokumentBeskrivelse
                    )
                    val behovsmeldingType = BehovsmeldingType.valueOf(
                        packet.soknadJson.at("/behovsmeldingType").textValue()
                            .let { if (it.isNullOrEmpty()) "SØKNAD" else it }
                    )
                    logger.info { "Sak til journalføring mottatt: ${mottattJournalpostData.soknadId} ($behovsmeldingType) med dokumenttittel ${mottattJournalpostData.dokumentBeskrivelse}" }
                    val pdf =
                        genererPdf(soknadToJson(mottattJournalpostData.soknadJson), mottattJournalpostData.soknadId)
                    try {
                        val journalpostResponse = opprettMottattJournalpost(
                            mottattJournalpostData.fnrBruker,
                            mottattJournalpostData.navnBruker,
                            mottattJournalpostData.dokumentBeskrivelse,
                            mottattJournalpostData.soknadId,
                            pdf,
                            behovsmeldingType,
                            packet.mottattDato.atStartOfDay()
                        )
                        forward(mottattJournalpostData, journalpostResponse, context)
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
            pdfClient.genererSoknadPdf(soknadJson)
        }.onSuccess {
            logger.info("PDF generert: $soknadId")
            Prometheus.pdfGenerertCounter.inc()
        }.onFailure {
            logger.error(it) { "Feilet under generering av PDF: $soknadId" }
        }.getOrThrow()

    private suspend fun opprettMottattJournalpost(
        fnrBruker: String,
        navnAvsender: String,
        dokumentBeskrivelse: String,
        soknadId: UUID,
        soknadPdf: ByteArray,
        behovsmeldingType: BehovsmeldingType,
        mottattDato: LocalDateTime? = null
    ) =
        kotlin.runCatching {
            joarkClient.arkiverSoknad(
                fnrBruker,
                navnAvsender,
                dokumentBeskrivelse,
                soknadId,
                soknadPdf,
                behovsmeldingType,
                soknadId.toString() + "HOTSAK_TIL_GOSYS",
                mottattDato
            )
        }.onSuccess {
            val journalpostnr = it
            logger.info("Opprettet journalpost med status mottatt i joark, journalpostNr: $journalpostnr")
            Prometheus.opprettettJournalpostMedStatusMottattCounter.inc()
        }.onFailure {
            logger.error(it) { "Feilet under opprettelse av journalpost med status mottatt for søknad: $soknadId" }
            throw it
        }.getOrThrow()

    private fun CoroutineScope.forward(
        mottattJournalpostData: MottattJournalpostData,
        joarkRef: String,
        context: MessageContext
    ) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.publish(mottattJournalpostData.fnrBruker, mottattJournalpostData.toJson(joarkRef, eventName))
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    logger.info("Opprettet journalpost med status mottatt i joark for: ${mottattJournalpostData.soknadId}")
                    sikkerlogg.info("Opprettet journalpost med status mottatt i joark for: ${mottattJournalpostData.soknadId}, fnr: ${mottattJournalpostData.fnrBruker})")
                }
                is CancellationException -> logger.warn("Cancelled: ${it.message}")
                else -> {
                    logger.error("Failed: ${it.message}. Soknad: ${mottattJournalpostData.soknadId}")
                }
            }
        }
    }
}

internal data class MottattJournalpostData(
    val fnrBruker: String,
    val navnBruker: String,
    val soknadId: UUID,
    val soknadJson: JsonNode,
    val sakId: String,
    val dokumentBeskrivelse: String
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
            it["soknadJson"] = this.soknadJson
        }.toJson()
    }
}
