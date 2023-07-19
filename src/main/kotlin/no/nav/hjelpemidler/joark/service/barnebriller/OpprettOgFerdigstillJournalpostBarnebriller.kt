package no.nav.hjelpemidler.joark.service.barnebriller

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService

private val log = KotlinLogging.logger {}
private val secureLog = KotlinLogging.logger("tjenestekall")

/**
 * Barnebrillevedtak er opprettet i optikerløsningen
 */
class OpprettOgFerdigstillJournalpostBarnebriller(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-barnebrillevedtak-opprettet") }
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
    private val JsonMessage.opprettet get() = this["opprettetDato"].asLocalDateTime()
    private val JsonMessage.sakId get() = this["sakId"].textValue()
    private val JsonMessage.brilleseddel get() = this["brilleseddel"]
    private val JsonMessage.bestillingsdato get() = this["bestillingsdato"].asLocalDate()
    private val JsonMessage.bestillingsreferanse get() = this["bestillingsreferanse"].textValue()
    private val JsonMessage.satsBeskrivelse get() = this["satsBeskrivelse"].textValue()
    private val JsonMessage.satsBeløp get() = this["satsBeløp"].decimalValue()
    private val JsonMessage.beløp get() = this["beløp"].decimalValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        coroutineScope {
            launch {
                val data = JournalpostBarnebrillevedtakData(
                    fnr = packet.fnr,
                    brukersNavn = packet.brukersNavn,
                    orgnr = packet.orgnr,
                    orgNavn = packet.orgNavn,
                    orgAdresse = packet.orgAdresse,
                    sakId = packet.sakId,
                    navnAvsender = packet.navnAvsender,
                    brilleseddel = packet.brilleseddel,
                    opprettet = packet.opprettet,
                    bestillingsdato = packet.bestillingsdato,
                    bestillingsreferanse = packet.bestillingsreferanse,
                    satsBeskrivelse = packet.satsBeskrivelse,
                    satsBeløp = packet.satsBeløp,
                    beløp = packet.beløp
                )
                log.info { "Sak til journalføring barnebriller mottatt, sakId: ${data.sakId}" }
                val fysiskDokument = journalpostService.genererPdf(data)
                val journalpostId = journalpostService.opprettInngåendeJournalpost(
                    fnrAvsender = data.fnr,
                    forsøkFerdigstill = true,
                ) {
                    dokument(fysiskDokument = fysiskDokument, dokumenttype = data.dokumenttype)
                    sakFraOptiker(data.sakId)
                    tittelFra(data.dokumenttype)
                    eksternReferanseId = "${data.sakId}BARNEBRILLEAPI"
                    datoMottatt = data.opprettet
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
        val fnr = data.fnr
        launch(Dispatchers.IO + SupervisorJob()) {
            context.publish(
                fnr,
                data.toJson(journalpostId, "hm-opprettetOgFerdigstiltBarnebrillerJournalpost")
            )
        }.invokeOnCompletion {
            val sakId = data.sakId
            when (it) {
                null -> {
                    log.info {
                        "Opprettet og ferdigstilte journalpost for barnebriller i joark for sakId: $sakId"
                    }
                    secureLog.info {
                        "Opprettet og ferdigstilte journalpost for barnebriller for sakId: $sakId, fnr: $fnr"
                    }
                }

                is CancellationException -> log.warn(it) { "Cancelled" }
                else -> log.error(it) {
                    "Kunne ikke opprette og ferdigstille journalpost for barnebriller, sakId: $sakId"
                }
            }
        }
    }
}
