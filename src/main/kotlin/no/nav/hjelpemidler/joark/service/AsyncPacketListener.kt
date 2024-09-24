package no.nav.hjelpemidler.joark.service

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

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
