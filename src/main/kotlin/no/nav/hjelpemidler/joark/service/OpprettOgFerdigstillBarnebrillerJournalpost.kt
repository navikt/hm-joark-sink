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

internal class OpprettOgFerdigstillBarnebrillerJournalpost(
    rapidsConnection: RapidsConnection,
    private val pdfClient: PdfClient,
    private val joarkClientV2: JoarkClientV2,
    private val eventName: String = "hm-opprettetOgFerdigstiltBarnebrillerJournalpost"
) : River.PacketListener {

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    private fun soknadToJson(soknad: JsonNode): String = objectMapper.writeValueAsString(soknad)

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-barnebrillevedtak-opprettet") }
            validate { it.requireKey("fnr", "orgnr", "navnAvsender", "eventId", "opprettetDato", "sakId") }
        }.register(this)
    }

    private val JsonMessage.fnr get() = this["fnr"].textValue()
    private val JsonMessage.orgnr get() = this["orgnr"].textValue()
    private val JsonMessage.navnAvsender get() = this["navnAvsender"].textValue()
    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.opprettetDato get() = this["opprettetDato"].textValue()
    private val JsonMessage.sakId get() = this["sakId"].textValue()

    private val DOKUMENTTITTEL = "Journalføring barnebriller" // TODO: finn ut hva denne skal være

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logger.error(problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    val journalpostBarnebrillerData = JournalpostBarnebrillerData(
                        fnr = packet.fnr,
                        orgnr = packet.orgnr,
                        sakId = packet.sakId,
                        navnAvsender = packet.navnAvsender,
                        dokumentTittel = DOKUMENTTITTEL
                    )
                    logger.info { "Sak til journalføring barnebriller mottatt" }
                    val pdf = genererPdf(objectMapper.writeValueAsString(journalpostBarnebrillerData), journalpostBarnebrillerData.sakId)
                    try {
                        val journalpostResponse = opprettOgFerdigstillBarnebrillerJournalpost(
                            fnr = journalpostBarnebrillerData.fnr,
                            orgnr = journalpostBarnebrillerData.orgnr,
                            pdf = pdf,
                            //soknadId = journalpostBarnebrillerData.soknadId,
                            sakId = journalpostBarnebrillerData.sakId,
                            navnAvsender = journalpostBarnebrillerData.navnAvsender,
                            dokumentTittel = DOKUMENTTITTEL,
                        )
                        forward(journalpostBarnebrillerData, journalpostResponse.journalpostNr, context)
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

    private suspend fun genererPdf(json: String, sakId: String) =
        kotlin.runCatching {
            pdfClient.genererBarnebrillePdf(json)
        }.onSuccess {
            logger.info("PDF generert: $sakId")
            Prometheus.pdfGenerertCounter.inc()
        }.onFailure {
            logger.error(it) { "Feilet under generering av PDF: $sakId" }
        }.getOrThrow()

    private suspend fun opprettOgFerdigstillBarnebrillerJournalpost(
        fnr: String,
        orgnr: String,
        pdf: ByteArray,
        //soknadId: String
        sakId: String,
        dokumentTittel: String,
        navnAvsender: String,
    ) =
        kotlin.runCatching {
            joarkClientV2.opprettOgFerdigstillJournalføringBarnebriller(
                fnr = fnr,
                orgnr = orgnr,
                sakId = sakId,
                dokumentTittel = dokumentTittel,
                navnAvsender = navnAvsender,
                pdf = pdf,
            )
        }.onSuccess {
            val journalpostnr = it.journalpostNr
            if (it.ferdigstilt) {
                logger.info("Opprettet og ferdigstilte journalpost i joark, journalpostNr: $journalpostnr")
            } else {
                logger.warn("Opprettet journalpost for barnebriller i joark, sakId: $sakId og journalpostNr: $journalpostnr, men klarte ikke å ferdigstille")
                throw BadRequestException("Klarte ikke å ferdigstille journalpost")
            }
            Prometheus.opprettettOgferdigstiltJournalpostCounter.inc()
        }.onFailure {
            logger.error(it) { "Feilet under opprettelse og ferdigstillelse journalpost for sakId: $sakId" }
            throw it
        }.getOrThrow()

    private fun CoroutineScope.forward(journalpostBarnebrillerData: JournalpostBarnebrillerData, joarkRef: String, context: MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.publish(journalpostBarnebrillerData.fnr, journalpostBarnebrillerData.toJson(joarkRef, eventName))
            Prometheus.soknadArkivertCounter.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    logger.info("Opprettet og ferdigstilte journalpost for barnebriller i joark for sakId: ${journalpostBarnebrillerData.sakId}")
                    sikkerlogg.info("Opprettet og ferdigstilte journalpost for barnebriller for sakId: ${journalpostBarnebrillerData.sakId}, fnr: ${journalpostBarnebrillerData.fnr})")
                }
                is CancellationException -> logger.warn("Cancelled: ${it.message}")
                else -> {
                    logger.error("Failed: ${it.message}. SakId: ${journalpostBarnebrillerData.sakId}")
                }
            }
        }
    }
}

internal data class JournalpostBarnebrillerData(
    val fnr: String,
    val orgnr: String,
    val sakId: String,
    val navnAvsender: String,
    val dokumentTittel: String
) {
    internal fun toJson(joarkRef: String, eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["fnr"] = this.fnr
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["orgnr"] = this.orgnr
            it["joarkRef"] = joarkRef
            it["sakId"] = this.sakId
            it["dokumentTittel"] = this.dokumentTittel
            it["eventId"] = UUID.randomUUID()
        }.toJson()
    }
}