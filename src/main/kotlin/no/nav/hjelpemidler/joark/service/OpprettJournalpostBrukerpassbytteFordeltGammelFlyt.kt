package no.nav.hjelpemidler.joark.service

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.joark.Configuration
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.domain.Sakstype
import no.nav.hjelpemidler.joark.jsonMapper
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Journalføring av brukerpassbytter som behandles i Gosys/Infotrygd
 */
class OpprettJournalpostBrukerpassbytteFordeltGammelFlyt(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
    private val eventName: String = Configuration.EVENT_NAME,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue(
                    "eventName",
                    "hm-brukerpassbytteFordeltGammelFlyt",
                )
            }
            validate { it.requireKey("id", "fnr", "brukerpassbytte") }
        }.register(this)
    }

    private val JsonMessage.bytteId get() = this["id"].textValue()

    private val JsonMessage.fnr get() = this["fnr"].textValue()

    private val JsonMessage.brukerpassbytte get() = this["brukerpassbytte"]

    private val JsonMessage.brukersNavn get() = this["brukerpassbytte"]["brukersNavn"].textValue()

    // TODO: send med soknadGjelder fra hm-soknadbehandling og bruk den her
    private val JsonMessage.søknadGjelder
        get() = this["soknadGjelder"].textValue() ?: Dokumenttype.SØKNAD_OM_HJELPEMIDLER.tittel

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val data = BehovsmeldingData(
            behovsmeldingId = UUID.fromString(packet.bytteId),
            fnrBruker = packet.fnr,
            behovsmeldingJson = jsonMapper.writeValueAsString(packet.brukerpassbytte),
            behovsmeldingGjelder = packet.søknadGjelder,
            navnBruker = packet.brukersNavn,
            sakstype = Sakstype.BRUKERPASS_BYTTE,
        )

        log.info {
            "Brukerpassbytte til arkivering mottatt, id: ${data.behovsmeldingId}, dokumenttittel: ${data.behovsmeldingGjelder}"
        }

        try {
            val journalpostId = journalpostService.arkiverBehovsmelding(
                fnrBruker = data.fnrBruker,
                behovsmeldingId = data.behovsmeldingId,
                behovsmeldingJson = packet.brukerpassbytte,
                sakstype = Sakstype.BRUKERPASS_BYTTE,
                dokumenttittel = data.behovsmeldingGjelder,
                eksternReferanseId = "${data.behovsmeldingId}HJE-DIGITAL-SOKNAD",
            )

            context.publish(data.fnrBruker, data.toJson(journalpostId, eventName))
            log.info("Brukerpassbytte arkivert i joark, id: ${data.behovsmeldingId}")
        } catch (e: Throwable) {
            log.error(e) { "Brukerpassbytte ble ikke arkivert i joark, id: ${data.behovsmeldingId}" }
            throw e
        }
    }
}
