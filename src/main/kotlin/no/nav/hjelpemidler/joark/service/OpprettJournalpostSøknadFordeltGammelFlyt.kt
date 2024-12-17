package no.nav.hjelpemidler.joark.service

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.publish
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Journalføring av søknader som behandles i Gosys/Infotrygd
 */
class OpprettJournalpostSøknadFordeltGammelFlyt(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireAny(
                    "eventName",
                    listOf("hm-søknadFordeltGammelFlyt"),
                )
            }
            validate {
                it.requireKey("fodselNrBruker", "soknadId", "erHast", "behovsmeldingType")
                it.interestedIn("soknadGjelder")
            }
        }.register(this)
    }

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val data: BehovsmeldingData = jsonMapper.readValue(packet.toJson())

        if (data.behovsmeldingId in skip) {
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
                dokumenttittel = data.behovsmeldingGjelder!!,
                eksternReferanseId = "${data.behovsmeldingId}HJE-DIGITAL-SOKNAD",
            )

            context.publish(data.fnrBruker, data.copy(joarkRef = journalpostId))
            log.info { "Søknad ble arkivert i Joark, søknadId: ${data.behovsmeldingId}, journalpostId: $journalpostId" }
        } catch (e: Throwable) {
            log.error(e) { "Søknad ble ikke arkivert i Joark, søknadId: ${data.behovsmeldingId}" }
            throw e
        }
    }
}

private val skip = setOf(
    "7c020fe0-cbe3-4bd2-81c6-ab62dadf44f6",
    "16565b25-1d9a-4dbb-b62e-8c68cc6a64c8",
    "ddfd0e1e-a493-4395-9a63-783a9c1fadf0",
    "99103106-dd24-4368-bf97-672f0b590ee3",
).map(UUID::fromString)
