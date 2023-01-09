package no.nav.hjelpemidler.joark.service.barnebriller

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.Dispatchers
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
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

internal class ResendBarnebrillerJournalpost(
    rapidsConnection: RapidsConnection,
    private val pdfClient: PdfClient,
    private val joarkClientV2: JoarkClientV2,
) : River.PacketListener {

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    private fun soknadToJson(soknad: JsonNode): String = objectMapper.writeValueAsString(soknad)

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-barnebrillevedtak-rekjor") }
            validate {
                it.requireKey(
                    "fnr",
                    "brukersNavn",
                    "orgnr",
                    "orgNavn",
                    "orgAdresse",
                    "navnAvsender",
                    "eventId",
                    "opprettetDato",
                    "sakId",
                    "brilleseddel",
                    "bestillingsdato",
                    "bestillingsreferanse",
                    "satsBeskrivelse",
                    "satsBeløp",
                    "beløp"
                )
            }
        }.register(this)
    }

    private val JsonMessage.fnr get() = this["fnr"].textValue()
    private val JsonMessage.brukersNavn get() = this["brukersNavn"].textValue()
    private val JsonMessage.orgnr get() = this["orgnr"].textValue()
    private val JsonMessage.orgNavn get() = this["orgNavn"].textValue()
    private val JsonMessage.orgAdresse get() = this["orgAdresse"].textValue()
    private val JsonMessage.navnAvsender get() = this["navnAvsender"].textValue()
    private val JsonMessage.eventId get() = this["eventId"].textValue()
    private val JsonMessage.opprettetDato get() = LocalDateTime.parse(this["opprettetDato"].textValue()).toLocalDate()
    private val JsonMessage.opprettetDatoTid get() = LocalDateTime.parse(this["opprettetDato"].textValue())
    private val JsonMessage.sakId get() = this["sakId"].textValue()
    private val JsonMessage.brilleseddel get() = this["brilleseddel"]
    private val JsonMessage.bestillingsdato get() = LocalDate.parse(this["bestillingsdato"].textValue())
    private val JsonMessage.bestillingsreferanse get() = this["bestillingsreferanse"].textValue()
    private val JsonMessage.satsBeskrivelse get() = this["satsBeskrivelse"].textValue()
    private val JsonMessage.satsBeløp get() = this["satsBeløp"].decimalValue()
    private val JsonMessage.beløp get() = this["beløp"].decimalValue()

    private val DOKUMENTTITTEL = "Journalføring barnebriller" // TODO: finn ut hva denne skal være

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logger.error(problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    val journalpostBarnebrillerData = ResendJournalpostBarnebrillerData(
                        fnr = packet.fnr,
                        brukersNavn = packet.brukersNavn,
                        orgnr = packet.orgnr,
                        orgNavn = packet.orgNavn,
                        orgAdresse = packet.orgAdresse,
                        sakId = packet.sakId,
                        navnAvsender = packet.navnAvsender,
                        dokumentTittel = DOKUMENTTITTEL,
                        brilleseddel = packet.brilleseddel,
                        opprettet = packet.opprettetDato,
                        bestillingsdato = packet.bestillingsdato,
                        bestillingsreferanse = packet.bestillingsreferanse,
                        satsBeskrivelse = packet.satsBeskrivelse,
                        satsBeløp = packet.satsBeløp,
                        beløp = packet.beløp
                    )
                    logger.info { "Sak til rejournalføring barnebriller mottatt" }
                    val pdf = genererPdf(
                        objectMapper.writeValueAsString(journalpostBarnebrillerData),
                        journalpostBarnebrillerData.sakId
                    )
                    try {
                        val journalpostResponse = opprettOgFerdigstillBarnebrillerJournalpost(
                            fnr = journalpostBarnebrillerData.fnr,
                            orgnr = journalpostBarnebrillerData.orgnr,
                            pdf = pdf,
                            // soknadId = journalpostBarnebrillerData.soknadId, // TODO: skal det følge med en soknadId fra hm-brille-api?
                            sakId = journalpostBarnebrillerData.sakId,
                            navnAvsender = journalpostBarnebrillerData.navnAvsender,
                            dokumentTittel = DOKUMENTTITTEL,
                            datoMottatt = packet.opprettetDatoTid.toString() + "Z"
                        )
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
        // soknadId: String
        sakId: String,
        dokumentTittel: String,
        navnAvsender: String,
        datoMottatt: String,
    ) =
        kotlin.runCatching {
            joarkClientV2.rekjørJournalføringBarnebriller(
                fnr = fnr,
                orgnr = orgnr,
                sakId = sakId,
                dokumentTittel = dokumentTittel,
                navnAvsender = navnAvsender,
                pdf = pdf,
                datoMottatt = datoMottatt
            )
        }.onSuccess {
            val journalpostnr = it.journalpostNr
            if (it.ferdigstilt) {
                logger.info("Rekjørte journalføring av barnebrillervedtak i joark, journalpostNr: $journalpostnr, sakId: $sakId")
            } else {
                logger.warn("Rekjørte journalføring av barnebrillervedtak i joark, sakId: $sakId og journalpostNr: $journalpostnr, men klarte ikke å ferdigstille")
                throw BadRequestException("Klarte ikke å ferdigstille journalpost")
            }
        }.onFailure {
            logger.error(it) { "Feilet under rekjøring av journalføring av barnebrillervedtak  journalpost for barnebriller for sakId: $sakId" }
            throw it
        }.getOrThrow()
}

internal data class ResendJournalpostBarnebrillerData(
    val fnr: String,
    val brukersNavn: String,
    val orgnr: String,
    val orgNavn: String,
    val orgAdresse: String,
    val sakId: String,
    val navnAvsender: String,
    val dokumentTittel: String,
    val brilleseddel: JsonNode,
    val opprettet: LocalDate,
    val bestillingsdato: LocalDate,
    val bestillingsreferanse: String,
    val satsBeskrivelse: String,
    val satsBeløp: BigDecimal,
    val beløp: BigDecimal,
)
