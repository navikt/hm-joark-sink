package no.nav.hjelpemidler.joark.service.hotsak

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.serialization.jackson.stringValueOrNull
import no.nav.hjelpemidler.serialization.jackson.uuidValue

private val log = KotlinLogging.logger {}

class BestillingAvvistOppdaterJournalpost(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "hm-BestillingAvvist") }
            validate {
                it.requireKey(
                    "saksnummer",
                    "søknadId",
                )
                // joarkRef kan være null, i så fall ignorer vi og må bruke interestedIn for å unngå exception over
                it.interestedIn("joarkRef")
            }
        }.register(this)
    }

    private val JsonMessage.sakId get() = this["saksnummer"].stringValue()
    private val JsonMessage.søknadId get() = this["søknadId"].uuidValue()
    private val JsonMessage.journalpostId get() = this["joarkRef"].stringValueOrNull()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val sakId = packet.sakId
        val søknadId = packet.søknadId
        val journalpostId = packet.journalpostId
        if (journalpostId == null) {
            log.warn { "Hopper over hendelse uten journalpostId, sakId: $sakId, søknadId: $søknadId" }
            return
        }
        if (journalpostId in skip) {
            log.warn { "Hopper over hendelse, journalpostId: $journalpostId, sakId: $sakId, søknadId: $søknadId" }
            return
        }
        journalpostService.leggTilPrefiksITitler(journalpostId = journalpostId, prefiks = "Avvist: ")
    }
}

private val skip = setOf<String>()
