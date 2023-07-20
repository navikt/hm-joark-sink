package no.nav.hjelpemidler.joark.service

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
import no.nav.hjelpemidler.domain.Dokumenttype
import no.nav.hjelpemidler.domain.Sakstype
import no.nav.hjelpemidler.joark.Configuration
import no.nav.hjelpemidler.joark.jsonMapper
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}
private val secureLog = KotlinLogging.logger("tjenestekall")

/**
 * Journalføring av søknader som behandles i Gosys/Infotrygd
 */
class OpprettJournalpostSøknadFordeltGammelFlyt(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
    private val eventName: String = Configuration.EVENT_NAME,
) : AsyncPacketListener {
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
    private val JsonMessage.søknadId get() = this["soknadId"].textValue().let(UUID::fromString)
    private val JsonMessage.søknadJson get() = this["soknad"]
    private val JsonMessage.søknadGjelder
        get() = this["soknadGjelder"].textValue() ?: Dokumenttype.SØKNAD_OM_HJELPEMIDLER.tittel

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        coroutineScope {
            launch {
                val data = SoknadData(
                    fnrBruker = packet.fnrBruker,
                    navnBruker = packet.navnBruker,
                    soknadJson = jsonMapper.writeValueAsString(packet.søknadJson),
                    soknadId = packet.søknadId,
                    soknadGjelder = packet.søknadGjelder,
                )
                if (skip(data.soknadId)) {
                    log.warn { "Hopper over søknad med søknadId: ${data.soknadId}" }
                    return@launch
                }
                log.info {
                    "Søknad til arkivering mottatt, søknadId: ${data.soknadId}, dokumenttittel: ${data.soknadGjelder}"
                }
                val journalpostId = journalpostService.arkiverSøknad(
                    fnrBruker = data.fnrBruker,
                    søknadId = data.soknadId,
                    søknadJson = packet.søknadJson,
                    sakstype = Sakstype.SØKNAD,
                    dokumenttittel = data.soknadGjelder,
                    eksternReferanseId = "${data.soknadId}HJE-DIGITAL-SOKNAD"
                )
                forward(data, journalpostId, context)
            }
        }
    }

    private fun CoroutineScope.forward(søknadData: SoknadData, journalpostId: String, context: MessageContext) {
        launch(Dispatchers.IO + SupervisorJob()) {
            context.publish(søknadData.fnrBruker, søknadData.toJson(journalpostId, eventName))
        }.invokeOnCompletion {
            when (it) {
                null -> {
                    log.info("Søknad arkivert i joark, søknadId: ${søknadData.soknadId}")
                    secureLog.info("Søknad arkivert med søknadId: ${søknadData.soknadId}, fnr: ${søknadData.fnrBruker}")
                }

                is CancellationException -> log.warn(it) { "Cancelled" }
                else -> log.error(it) {
                    "Søknad ble ikke arkivert i joark, søknadId: ${søknadData.soknadId}"
                }
            }
        }
    }
}

private fun skip(søknadId: UUID): Boolean =
    søknadId in setOf<UUID>(
        UUID.fromString("7c020fe0-cbe3-4bd2-81c6-ab62dadf44f6"),
        UUID.fromString("16565b25-1d9a-4dbb-b62e-8c68cc6a64c8"),
        UUID.fromString("ddfd0e1e-a493-4395-9a63-783a9c1fadf0"),
        UUID.fromString("99103106-dd24-4368-bf97-672f0b590ee3")
    )

private data class SoknadData(
    val fnrBruker: String,
    val navnBruker: String,
    val soknadId: UUID,
    val soknadJson: String,
    val soknadGjelder: String,
) {
    @Deprecated("Bruk Jackson direkte")
    fun toJson(journalpostId: String, eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["soknadId"] = this.soknadId
            it["eventName"] = eventName
            it["opprettet"] = LocalDateTime.now()
            it["fodselNrBruker"] = this.fnrBruker // @deprecated
            it["fnrBruker"] = this.fnrBruker
            it["joarkRef"] = journalpostId
            it["eventId"] = UUID.randomUUID()
            it["soknadGjelder"] = soknadGjelder
        }.toJson()
    }
}
