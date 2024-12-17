package no.nav.hjelpemidler.joark.service

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.logging.secureLog

private val log = KotlinLogging.logger {}

interface AsyncPacketListener : River.PacketListener {
    suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext)

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) =
        runBlocking(Dispatchers.IO) {
            onPacketAsync(packet, context)
        }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.info { "Validering av melding feilet, se secureLog for detaljer" }
        secureLog.info {
            "Validering av melding feilet: '${problems.toExtendedReport()}'"
        }
    }
}
