package no.nav.hjelpemidler.joark.service.hotsak

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.dokarkiv.models.DokumentInfo
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.rapids_and_rivers.eventId
import no.nav.hjelpemidler.rapids_and_rivers.uuidSetOf
import no.nav.hjelpemidler.serialization.jackson.localDateTimeValue
import no.nav.hjelpemidler.serialization.jackson.stringValueOrNull
import no.nav.hjelpemidler.serialization.jackson.uuidValue

private val log = KotlinLogging.logger {}

class BestillingAvvistOppdaterJournalpost(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "hm-BestillingAvvist-saf-beriket") }
            validate {
                it.requireKey(
                    "eventId",
                    "saksnummer",
                    "søknadId",
                    "opprettet",
                    "tittel",
                    "dokumenter"
                )
                // Joarkref kan være null, i så fall ignorer vi og må bruke interestedIn for å unngå exception over
                it.interestedIn("joarkRef")
            }
        }.register(this)
    }

    private val JsonMessage.sakId get() = this["saksnummer"].stringValue()
    private val JsonMessage.søknadId get() = this["søknadId"].uuidValue()
    private val JsonMessage.journalpostId get() = this["joarkRef"].stringValueOrNull()
    private val JsonMessage.opprettet get() = this["opprettet"].localDateTimeValue()
    private val JsonMessage.tittel get() = this["tittel"].stringValue()
    private val JsonMessage.dokumenter
        get() = this["dokumenter"].mapTo(mutableListOf()) {
            DokumentInfo(
                dokumentInfoId = it["dokumentInfoId"].stringValue(),
                tittel = it["tittel"].stringValue(),
            )
        } // fixme -> deserialiser direkte

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val eventId = packet.eventId
        val sakId = packet.sakId
        val søknadId = packet.søknadId
        val journalpostId = packet.journalpostId
        val opprettet = packet.opprettet
        if (journalpostId == null) {
            log.info {
                "Ignoring event due to journalpostId: null, eventId: $eventId, sakId: $sakId, søknadId: $søknadId, opprettet: $opprettet"
            }
            return
        }
        if (eventId in skip) {
            log.warn {
                "Skipping event eventId: $eventId, sakId: $sakId, søknadId: $søknadId, opprettet: $opprettet"
            }
            return
        }
        log.info {
            "Mottok melding om at en bestilling har blitt avvist, markerer den som avvist i dokarkiv (eventId: $eventId, sakId: $sakId, søknadId: $søknadId, journalpostId: $journalpostId)"
        }
        journalpostService.endreTittel(
            journalpostId = journalpostId,
            tittel = packet.tittel,
            dokumenter = packet.dokumenter
        )
    }
}

private val skip = uuidSetOf(
    "8f406d64-9eb2-4a04-ad41-ccce503e1f27",
    "4b27dbf8-b1d6-47b3-9bab-a2a775bc6ad4",
    "8c517218-25dc-44f5-bfd7-c1d35ca8836e",
)
