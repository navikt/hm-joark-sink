package no.nav.hjelpemidler.joark.service.barnebriller

import io.ktor.server.plugins.BadRequestException
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
import no.nav.hjelpemidler.joark.joark.JoarkClientV4
import no.nav.hjelpemidler.joark.metrics.Prometheus
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class OpprettOgFerdigstillBarnebrillevedtakJournalpost(
    rapidsConnection: RapidsConnection,
    private val joarkClientV4: JoarkClientV4,
    private val eventName: String = "hm-opprettetOgFerdigstiltBarnebrillevedtakJournalpost",
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-manuelt-barnebrillevedtak-opprettet") }
            validate {
                it.requireKey(
                    "saksnummer",
                    "fnrBruker",
                    "navnBruker",
                    "navnAvsender",
                    "opprettet",
                    "pdf"
                )
            }
        }.register(this)
    }

    private val JsonMessage.fnr get() = this["fnrBruker"].textValue()
    private val JsonMessage.brukersNavn get() = this["navnBruker"].textValue()
    private val JsonMessage.navnAvsender get() = this["navnAvsender"].textValue()
    private val JsonMessage.opprettetDato get() = LocalDateTime.parse(this["opprettet"].textValue()).toLocalDate()
    private val JsonMessage.sakId get() = this["saksnummer"].textValue()
    private val JsonMessage.pdf get() = this["pdf"].binaryValue()

    private val DOKUMENTTITTEL = "Journalføring barnebrillevedtak"

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logger.error(problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    val journalpostBarnebrillevedtakData = JournalpostBarnebrillevedtakData(
                        fnr = packet.fnr,
                        brukersNavn = packet.brukersNavn,
                        sakId = packet.sakId,
                        navnAvsender = packet.navnAvsender,
                        dokumentTittel = DOKUMENTTITTEL,
                        opprettet = packet.opprettetDato,
                        pdf = packet.pdf
                    )
                    logger.info { "Manuelt barnebrillevedtak til journalføring mottatt" }
                    try {
                        val journalpostResponse = opprettOgFerdigstillBarnebrillevedtakJournalpost(
                            fnr = journalpostBarnebrillevedtakData.fnr,
                            pdf = journalpostBarnebrillevedtakData.pdf,
                            sakId = journalpostBarnebrillevedtakData.sakId,
                            navnAvsender = journalpostBarnebrillevedtakData.navnAvsender,
                            dokumentTittel = DOKUMENTTITTEL
                        )
                        forward(journalpostBarnebrillevedtakData, journalpostResponse.journalpostNr, context)
                    } catch (e: Exception) {
                        // Forsøk på arkivering av dokument med lik eksternReferanseId vil feile med 409 frå Joark/Dokarkiv si side
                        // Dette skjer kun dersom vi har arkivert vedtak tidlegare (prosessering av samme melding fleire gongar)
                        if (e.message != null && e.message!!.contains("409 Conflict")) return@launch
                        throw e
                    }
                }
            }
        }
    }

    private suspend fun opprettOgFerdigstillBarnebrillevedtakJournalpost(
        fnr: String,
        pdf: ByteArray,
        sakId: String,
        dokumentTittel: String,
        navnAvsender: String,
    ) =
        kotlin.runCatching {
            joarkClientV4.opprettOgFerdigstillBarnebrillevedtak(
                fnr = fnr,
                sakId = sakId,
                dokumentTittel = dokumentTittel,
                navnAvsender = navnAvsender,
                pdf = pdf
            )
        }.onSuccess {
            val journalpostnr = it.journalpostNr
            if (it.ferdigstilt) {
                logger.info("Opprettet og ferdigstilte journalpost  for barnebrillevedtak i joark, journalpostNr: $journalpostnr og sakId: $sakId")
            } else {
                logger.warn("Opprettet journalpost for barnebrillevedtak i joark, sakId: $sakId og journalpostNr: $journalpostnr, men klarte ikke å ferdigstille")
                throw BadRequestException("Klarte ikke å ferdigstille journalpost")
            }
            Prometheus.opprettettOgferdigstiltJournalpostCounter.inc()
        }.onFailure {
            logger.error(it) { "Feilet under opprettelse og ferdigstillelse journalpost for barnebrillevedtak for sakId: $sakId" }
            throw it
        }.getOrThrow()

    private fun CoroutineScope.forward(
        journalpostBarnebrillevedtakData: JournalpostBarnebrillevedtakData,
        joarkRef: String,
        context: MessageContext,
    ) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.publish(
                journalpostBarnebrillevedtakData.fnr,
                journalpostBarnebrillevedtakData.toJson(joarkRef, eventName)
            )
            Prometheus.soknadArkivertCounter.inc()
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    logger.info("Opprettet og ferdigstilte journalpost for barnebrillevedtak i joark for sakId: ${journalpostBarnebrillevedtakData.sakId}")
                    sikkerlogg.info("Opprettet og ferdigstilte journalpost for barnebrillevedtak for sakId: ${journalpostBarnebrillevedtakData.sakId}, fnr: ${journalpostBarnebrillevedtakData.fnr})")
                }

                is CancellationException -> logger.warn("Cancelled: ${it.message}")
                else -> {
                    logger.error("Failed: ${it.message}. SakId: ${journalpostBarnebrillevedtakData.sakId}")
                }
            }
        }
    }
}

internal data class JournalpostBarnebrillevedtakData(
    val fnr: String,
    val brukersNavn: String,
    val sakId: String,
    val navnAvsender: String,
    val dokumentTittel: String,
    val opprettet: LocalDate,
    val pdf: ByteArray,
) {
    internal fun toJson(joarkRef: String, eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["fnr"] = this.fnr
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["joarkRef"] = joarkRef
            it["sakId"] = this.sakId
            it["dokumentTittel"] = this.dokumentTittel
            it["eventId"] = UUID.randomUUID()
        }.toJson()
    }
}
