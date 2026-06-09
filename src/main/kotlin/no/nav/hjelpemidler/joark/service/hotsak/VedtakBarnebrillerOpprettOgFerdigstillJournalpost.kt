package no.nav.hjelpemidler.joark.service.hotsak

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.configuration.HotsakApplicationId
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.kafka.KafkaEvent
import no.nav.hjelpemidler.kafka.KafkaMessage
import no.nav.hjelpemidler.logging.logStatement
import no.nav.hjelpemidler.rapids_and_rivers.publish
import no.nav.hjelpemidler.serialization.jackson.enumValueOrNull
import no.nav.hjelpemidler.serialization.jackson.localDateTimeValue
import no.nav.hjelpemidler.serialization.jackson.stringValueOrNull
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Vedtak for barnebrillesak er fattet i Hotsak, journalfør utgående vedtaksbrev
 */
class VedtakBarnebrillerOpprettOgFerdigstillJournalpost(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "hm-manuelt-barnebrillevedtak-opprettet") }
            validate {
                it.requireKey(
                    "saksnummer",
                    "fnrBruker",
                    "opprettet",
                    "pdf"
                )
                it.interestedIn(
                    "brevId",
                    "brevdistribusjonId",
                    "brevsendingId",
                    "vedtaksstatus",
                    "opprettetAv",
                ) // todo -> "brevsendingId" på sikt
            }
        }.register(this)
    }

    private val JsonMessage.sakId get() = this["saksnummer"].stringValue()
    private val JsonMessage.brevId: String? get() = this["brevId"].stringValueOrNull()
    private val JsonMessage.brevdistribusjonId: String? get() = this["brevdistribusjonId"].stringValueOrNull()
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].stringValue()
    private val JsonMessage.vedtaksstatus get() = this["vedtaksstatus"].enumValueOrNull<Vedtaksstatus>()
    private val JsonMessage.fysiskDokument get() = this["pdf"].binaryValue()
    private val JsonMessage.opprettet get() = this["opprettet"].localDateTimeValue()
    private val JsonMessage.opprettetAv: String? get() = this["opprettetAv"].stringValueOrNull()

    @Deprecated("Fjernes")
    private val JsonMessage.brevsendingId: String? get() = this["brevsendingId"].stringValueOrNull()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val sakId = packet.sakId
        val brevId = packet.brevId
        val data = JournalpostBarnebrillevedtakData(
            sakId = sakId,
            brevId = brevId,
            brevdistribusjonId = packet.brevdistribusjonId,
            fnr = packet.fnrBruker,
            dokumentTittel = "Journalføring barnebrillevedtak",
            brevsendingId = packet.brevsendingId,
            opprettet = packet.opprettet,
        )
        log.info {
            logStatement(
                "Vedtaksbrev fra Hotsak for barnebriller til journalføring mottatt",
                "sakId" to data.sakId,
                "brevId" to data.brevId,
                "brevdistribusjonId" to data.brevdistribusjonId,
                "brevsendingId" to data.brevsendingId,
            )
        }
        val dokumenttype =
            when (packet.vedtaksstatus) {
                Vedtaksstatus.INNVILGET -> Dokumenttype.VEDTAKSBREV_BARNEBRILLER_HOTSAK_INNVILGELSE
                Vedtaksstatus.AVSLÅTT -> Dokumenttype.VEDTAKSBREV_BARNEBRILLER_HOTSAK_AVSLAG
                null -> Dokumenttype.VEDTAKSBREV_BARNEBRILLER_HOTSAK
            }

        try {
            val journalpostId = journalpostService.opprettUtgåendeJournalpost(
                fnrMottaker = data.fnr,
                dokumenttype = dokumenttype,
                eksternReferanseId = "${sakId}BARNEBRILLEVEDTAK",
                forsøkFerdigstill = true
            ) {
                dokument(fysiskDokument = packet.fysiskDokument)
                hotsak(sakId)
                tilleggsopplysninger(
                    "id" to data.brevdistribusjonId,
                    "sakId" to sakId,
                    "brevId" to brevId,
                    prefix = HotsakApplicationId.application,
                )
                opprettetAv = packet.opprettetAv
            }

            context.publish(
                data.fnr,
                data.copy(journalpostId = journalpostId),
            )
            log.info { "Opprettet og ferdigstilte journalpost for vedtaksbrev for barnebriller i Joark for sakId: $sakId, brevId: $brevId, journalpostId: $journalpostId" }
        } catch (e: Throwable) {
            log.error(e) { "Klarte ikke å opprette og ferdigstille journalpost for vedtak om barnebriller i Joark for sakId: ${data.sakId}, brevId: $brevId" }
            throw e
        }
    }
}

@KafkaEvent("hm-opprettetOgFerdigstiltBarnebrillevedtakJournalpost")
data class JournalpostBarnebrillevedtakData(
    val sakId: String,
    val brevId: String? = null, // todo -> ikke nullable
    val brevdistribusjonId: String? = null, // todo -> ikke nullable
    @JsonProperty("joarkRef")
    val journalpostId: String? = null,
    val fnr: String,
    val dokumentTittel: String,
    @Deprecated("Fjernes")
    val brevsendingId: String? = null,
    override val eventId: UUID = UUID.randomUUID(),
    val opprettet: LocalDateTime,
) : KafkaMessage

enum class Vedtaksstatus {
    INNVILGET,
    AVSLÅTT
}
