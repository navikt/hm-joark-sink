package no.nav.hjelpemidler.joark.service

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.Configuration
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.domain.Sakstype
import no.nav.hjelpemidler.joark.jsonMapper
import java.util.UUID

private val log = KotlinLogging.logger {}

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
                    listOf("hm-søknadFordeltGammelFlyt"),
                )
            }
            validate { it.requireKey("fodselNrBruker", "soknadId", "erHast", "behovsmeldingType") }
            validate { it.interestedIn("soknadGjelder") }
        }.register(this)
    }

    private val JsonMessage.fnrBruker get() = this["fodselNrBruker"].textValue()
    private val JsonMessage.søknadId get() = this["soknadId"].textValue().let(UUID::fromString)
    private val JsonMessage.søknadGjelder
        get() = this["soknadGjelder"].textValue() ?: Dokumenttype.SØKNAD_OM_HJELPEMIDLER.tittel
    private val JsonMessage.sakstype get() = this["behovsmeldingType"].textValue().let(Sakstype::valueOf)
    private val JsonMessage.erHast get() = this["erHast"].booleanValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val data = BehovsmeldingData(
            fnrBruker = packet.fnrBruker,
            behovsmeldingId = packet.søknadId,
            behovsmeldingGjelder = packet.søknadGjelder,
            sakstype = packet.sakstype,
            erHast = packet.erHast,
        )

        if (skip(data.behovsmeldingId)) {
            log.warn { "Hopper over søknad med søknadId: ${data.behovsmeldingId}" }
            return
        }

        log.info {
            "Søknad til arkivering mottatt, søknadId: ${data.behovsmeldingId}, sakstype: ${data.sakstype}, dokumenttittel: ${data.behovsmeldingGjelder}, erHast: ${data.erHast}"
        }

        try {
            val journalpostId = journalpostService.arkiverBehovsmelding(
                fnrBruker = data.fnrBruker,
                behovsmeldingId = data.behovsmeldingId,
                sakstype = data.sakstype,
                dokumenttittel = data.behovsmeldingGjelder,
                eksternReferanseId = "${data.behovsmeldingId}HJE-DIGITAL-SOKNAD",
            )

            context.publish(data.fnrBruker, data.toJson(journalpostId, eventName))
            log.info { "Søknad ble arkivert i Joark, søknadId: ${data.behovsmeldingId}, journalpostId: $journalpostId" }
        } catch (e: Throwable) {
            log.error(e) { "Søknad ble ikke arkivert i Joark, søknadId: ${data.behovsmeldingId}" }
            throw e
        }
    }
}

private fun skip(søknadId: UUID): Boolean =
    søknadId in setOf(
        "7c020fe0-cbe3-4bd2-81c6-ab62dadf44f6",
        "16565b25-1d9a-4dbb-b62e-8c68cc6a64c8",
        "ddfd0e1e-a493-4395-9a63-783a9c1fadf0",
        "99103106-dd24-4368-bf97-672f0b590ee3",
    ).mapTo(mutableSetOf<UUID>(), UUID::fromString).toSet()
