package no.nav.hjelpemidler.joark.service.hotsak

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.Dokumenttype
import no.nav.hjelpemidler.joark.service.JournalpostService
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}
private val secureLog = KotlinLogging.logger("tjenestekall")

class VedtakBarnebrillerOpprettOgFerdigstillJournalpost(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
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

    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.navnBruker get() = this["navnBruker"].textValue()
    private val JsonMessage.navnAvsender get() = this["navnAvsender"].textValue()
    private val JsonMessage.opprettet get() = this["opprettet"].asLocalDateTime()
    private val JsonMessage.sakId get() = this["saksnummer"].textValue()
    private val JsonMessage.fysiskDokument get() = this["pdf"].binaryValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        coroutineScope {
            launch {
                val sakId = packet.sakId
                val data = JournalpostBarnebrillevedtakData(
                    fnr = packet.fnrBruker,
                    brukersNavn = packet.navnBruker,
                    sakId = sakId,
                    navnAvsender = packet.navnAvsender,
                    dokumentTittel = "Journalføring barnebrillevedtak",
                    opprettet = packet.opprettet,
                    pdf = packet.fysiskDokument
                )
                log.info { "Manuelt barnebrillevedtak til journalføring mottatt, sakId: ${data.sakId}" }
                val dokumenttype = Dokumenttype.VEDTAKSBREV_BARNEBRILLER_HOTSAK
                val journalpostId = journalpostService.opprettUtgåendeJournalpost(
                    fnrAvsender = data.fnr,
                    navnAvsender = data.navnAvsender,
                    forsøkFerdigstill = true
                ) {
                    dokument(data.pdf, dokumenttype)
                    sakFraHotsak(sakId)
                    tittelFra(dokumenttype)
                    eksternReferanseId = sakId + "BARNEBRILLEVEDTAK"
                }
                forward(journalpostId, data, context)
            }
        }
    }

    private fun CoroutineScope.forward(
        journalpostId: String,
        data: JournalpostBarnebrillevedtakData,
        context: MessageContext,
    ) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.publish(
                data.fnr,
                data.toJson(
                    journalpostId,
                    "hm-opprettetOgFerdigstiltBarnebrillevedtakJournalpost"
                )
            )
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    log.info("Opprettet og ferdigstilte journalpost for barnebrillevedtak i joark for sakId: ${data.sakId}")
                    secureLog.info("Opprettet og ferdigstilte journalpost for barnebrillevedtak for sakId: ${data.sakId}, fnr: ${data.fnr}")
                }

                is CancellationException -> log.warn(it) {
                    "Cancelled"
                }

                else -> {
                    log.error(it) {
                        "Klarte ikke å opprettet og ferdigstille journalpost for barnebrillevedtak i joark for sakId: ${data.sakId}"
                    }
                }
            }
        }
    }
}

private data class JournalpostBarnebrillevedtakData(
    val fnr: String,
    val brukersNavn: String,
    val sakId: String,
    val navnAvsender: String,
    val dokumentTittel: String,
    val opprettet: LocalDateTime,
    val pdf: ByteArray,
) {
    @Deprecated("Bruk Jackson direkte")
    fun toJson(journalpostId: String, eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["fnr"] = this.fnr
            it["eventName"] = eventName
            it["opprettet"] = opprettet
            it["joarkRef"] = journalpostId
            it["sakId"] = this.sakId
            it["dokumentTittel"] = this.dokumentTittel
            it["eventId"] = UUID.randomUUID()
        }.toJson()
    }
}
