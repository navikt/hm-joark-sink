package no.nav.hjelpemidler.joark.service.hotsak

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.domain.Sakstype
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.kafka.KafkaEvent
import no.nav.hjelpemidler.kafka.KafkaMessage
import no.nav.hjelpemidler.rapids_and_rivers.publish
import no.nav.hjelpemidler.serialization.jackson.enumValueOrNull
import no.nav.hjelpemidler.serialization.jackson.uuidValue
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Automatisk journalføring av saker fra Hotsak.
 */
class SakOpprettetOpprettOgFerdigstillJournalpost(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "hm-sakOpprettet") }
            validate {
                it.requireKey("soknadId", "soknadGjelder", "sakId", "fnrBruker")
                it.interestedIn("behovsmeldingType")
            }
        }.register(this)
    }

    private val JsonMessage.søknadId get() = this["soknadId"].uuidValue()
    private val JsonMessage.søknadGjelder get() = this["soknadGjelder"].textValue()
    private val JsonMessage.sakId get() = this["sakId"].textValue()
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.sakstype get() = this["behovsmeldingType"].enumValueOrNull<Sakstype>()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val data = JournalpostData(
            fnrBruker = packet.fnrBruker,
            soknadId = packet.søknadId,
            sakId = packet.sakId,
            dokumentTittel = packet.søknadGjelder
        )

        val sakstype = packet.sakstype ?: error("Mangler sakstype for søknadId: ${data.soknadId}")
        val dokumenttype = sakstype.dokumenttype

        log.info {
            "Sak til journalføring mottatt, søknadId: ${data.soknadId}, sakId: ${data.sakId}, dokumenttype: $dokumenttype, dokumenttittel: '${data.dokumentTittel}', sakstype: $sakstype"
        }

        try {
            val fysiskDokument = journalpostService.hentBehovsmeldingPdf(data.soknadId)
            val journalpostId = journalpostService.opprettInngåendeJournalpost(
                fnrAvsender = data.fnrBruker,
                dokumenttype = dokumenttype,
                eksternReferanseId = "${data.soknadId}HOTSAK",
                forsøkFerdigstill = true,
            ) {
                dokument(
                    fysiskDokument = fysiskDokument,
                    dokumenttittel = data.dokumentTittel
                )
                hotsak(data.sakId)
            }.journalpostId

            context.publish(data.fnrBruker, data.copy(joarkRef = journalpostId))
            log.info { "Opprettet og ferdigstilte journalpost i Joark for søknadId: ${data.soknadId}" }
        } catch (e: Throwable) {
            log.error(e) { "Klarte ikke å opprettet og ferdigstille journalpost i Joark for søknadId: ${data.soknadId}" }
            throw e
        }
    }
}

@KafkaEvent("hm-opprettetOgFerdigstiltJournalpost")
private data class JournalpostData(
    val soknadId: UUID,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val fnrBruker: String,
    val joarkRef: String? = null,
    val sakId: String,
    val dokumentTittel: String,
    override val eventId: UUID = UUID.randomUUID(),
) : KafkaMessage {
    @Deprecated("Bruk fnrBruker")
    val fodselNrBruker by this::fnrBruker
}
