package no.nav.hjelpemidler.joark.service

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.River

private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class RiverRequiredKeyMissingException(message: String) : Exception(message)

interface PacketListenerWithOnError : River.PacketListener {
    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerlogg.info("River required keys had problems in parsing message from rapid: ${problems.toExtendedReport()}")
        throw RiverRequiredKeyMissingException("River required keys had problems in parsing message from rapid, see Kibana index tjenestekall-* (sikkerlogg) for details")
    }
}
