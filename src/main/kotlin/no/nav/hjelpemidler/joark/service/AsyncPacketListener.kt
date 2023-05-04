package no.nav.hjelpemidler.joark.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.River

private val log = KotlinLogging.logger {}
private val secureLog = KotlinLogging.logger("tjenestekall")

interface AsyncPacketListener : River.PacketListener {
    suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext)

    override fun onPacket(packet: JsonMessage, context: MessageContext) =
        runBlocking(Dispatchers.IO) {
            onPacketAsync(packet, context)
        }

    override fun onError(problems: MessageProblems, context: MessageContext) =
        secureLog.info {
            problems.toExtendedReport()
        }
}
