package no.nav.hjelpemidler.joark.service.hotsak

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.joark.joark.JoarkClientV2
import no.nav.hjelpemidler.joark.service.PacketListenerWithOnError
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal class MerkAvvistBestilling(
    rapidsConnection: RapidsConnection,
    private val joarkClientV2: JoarkClientV2
) : PacketListenerWithOnError {

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

                // Joarkref kan være null, i såfall ignorer vi og må bruke interestedIn for å unngå exception over
                it.interestedIn("joarkRef")
            }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue()!!.let { UUID.fromString(it) }!!
    private val JsonMessage.sakId get() = this["saksnummer"].textValue()
    private val JsonMessage.søknadId get() = this["søknadId"].textValue()?.let { UUID.fromString(it) }
    private val JsonMessage.opprettet get() = this["opprettet"].asLocalDateTime()
    private val JsonMessage.joarkRef get() = this["joarkRef"].textValue()
    private val JsonMessage.tittel get() = this["tittel"].textValue()!!
    private val JsonMessage.dokumenter
        get() = this["dokumenter"].map {
            Pair(it["dokumentInfoId"].textValue()!!, it["tittel"].textValue()!!)
        }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        if (packet.joarkRef == null) {
            logger.info("Ignoring event due to joarkRef=null eventId=${packet.eventId}, sakId=${packet.sakId}, søknadId=${packet.søknadId}, opprettet=${packet.opprettet}")
            return
        }

        if (inSkipList(packet.eventId)) {
            logger.warn("Skipping event eventId=${packet.eventId}, sakId=${packet.sakId}, søknadId=${packet.søknadId}, opprettet=${packet.opprettet}")
            return
        }

        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    logger.info("Mottok melding om at en bestilling har blitt avvist, merker den i joark som avvist (eventId=${packet.eventId} sakId=${packet.sakId}, søknadId=${packet.søknadId}, joarkRef=${packet.joarkRef})")
                    joarkClientV2.omdøpAvvistBestilling(packet.joarkRef, packet.tittel, packet.dokumenter)
                }
            }
        }
    }

    private fun inSkipList(eventId: UUID): Boolean {
        val skipList = listOf<String>()
        return skipList.contains(eventId.toString())
    }
}
