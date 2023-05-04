package no.nav.hjelpemidler.joark.service.hotsak

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.dokarkiv.models.DokumentInfo
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import java.util.UUID

private val log = KotlinLogging.logger {}

class BestillingAvvistOppdaterJournalpost(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-BestillingAvvist-saf-beriket") }
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

    private val JsonMessage.eventId get() = this["eventId"].textValue().let { UUID.fromString(it) }
    private val JsonMessage.sakId get() = this["saksnummer"].textValue()
    private val JsonMessage.søknadId get() = this["søknadId"].textValue()?.let { UUID.fromString(it) }
    private val JsonMessage.journalpostId get() = this["joarkRef"].textValue()
    private val JsonMessage.opprettet get() = this["opprettet"].asLocalDateTime()
    private val JsonMessage.tittel get() = this["tittel"].textValue()
    private val JsonMessage.dokumenter
        get() = this["dokumenter"].map {
            DokumentInfo(
                dokumentInfoId = it["dokumentInfoId"].textValue(),
                tittel = it["tittel"].textValue(),
            )
        }

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
        if (skip(eventId)) {
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

private fun skip(eventId: UUID): Boolean {
    val skip = setOf<UUID>()
    return eventId in skip
}
