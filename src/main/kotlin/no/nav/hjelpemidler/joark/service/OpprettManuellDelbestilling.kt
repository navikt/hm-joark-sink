package no.nav.hjelpemidler.joark.service

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.Configuration
import no.nav.hjelpemidler.kafka.KafkaMessage
import no.nav.hjelpemidler.rapids_and_rivers.publish
import no.nav.hjelpemidler.serialization.jackson.jsonToValue
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Journalføring av delbestillinger som behandles manuelt i Gosys/Oebs
 */
class OpprettManuellDelbestilling(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireAny(
                    "eventName",
                    listOf("hm-OpprettManuellDelbestilling"),
                )
            }
            validate {
                it.requireKey("saksnummer", "brukersFnr", "mottattTidspunkt")
            }
        }.register(this)
    }

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val data: ManuellDelbestillingData = jsonToValue(packet.toJson())

        log.info {
            "Manuell Delbestillings-søknad til arkivering mottatt, søknadNummer: ${data.saksnummer}, dokumenttittel: ${data.dokumentTittel}"
        }

        try {
            val journalpostId = journalpostService.arkiverDelbestilling(
                saksnummer = data.saksnummer,
                fnrBruker = data.brukersFnr,
                datoMottatt = data.mottattTidspunkt,
                dokumenttittel = data.dokumentTittel,
                eksternReferanseId = data.eksternReferanseId,
            )

            context.publish(data.brukersFnr, data.copy(joarkRef = journalpostId))
            log.info { "Søknad ble arkivert i Joark, saksnummer: ${data.saksnummer}, journalpostId: $journalpostId" }
        } catch (e: Throwable) {
            log.error(e) { "Søknad ble ikke arkivert i Joark, saksnummer: ${data.saksnummer}" }
            throw e
        }
    }
}

data class ManuellDelbestillingData(
    val saksnummer: Long,
    val brukersFnr: String,
    val mottattTidspunkt: LocalDateTime,
    val joarkRef: String? = null,
) : KafkaMessage {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    override val eventId: UUID = UUID.randomUUID()

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    override val eventName: String = Configuration.DELBESTILLING_EVENT_NAME

    val dokumentTittel = "Bestilling av deler" // TODO: Spør Trygve om hva som er korrekt dokumenttittel
    val eksternReferanseId = "${saksnummer}HJE-DIGITAL-DELBESTILLING"
}
