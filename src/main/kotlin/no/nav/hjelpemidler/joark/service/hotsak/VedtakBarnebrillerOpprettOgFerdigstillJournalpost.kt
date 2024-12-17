package no.nav.hjelpemidler.joark.service.hotsak

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.Hendelse
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.publish
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import java.time.LocalDateTime

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
                    "vedtaksstatus",
                    "opprettetAv"
                )
            }
        }.register(this)
    }

    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.opprettet get() = this["opprettet"].asLocalDateTime()
    private val JsonMessage.sakId get() = this["saksnummer"].textValue()
    private val JsonMessage.fysiskDokument get() = this["pdf"].binaryValue()
    private val JsonMessage.vedtaksstatus get() = this["vedtaksstatus"].textValue()?.let(Vedtaksstatus::valueOf)
    private val JsonMessage.opprettetAv: String? get() = this["opprettetAv"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val sakId = packet.sakId
        val data = JournalpostBarnebrillevedtakData(
            fnr = packet.fnrBruker,
            sakId = sakId,
            dokumentTittel = "Journalføring barnebrillevedtak",
            opprettet = packet.opprettet,
        )
        log.info { "Manuelt barnebrillevedtak til journalføring mottatt, sakId: ${data.sakId}" }
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
                opprettetAv = packet.opprettetAv
            }

            context.publish(
                data.fnr,
                data.copy(joarkRef = journalpostId),
            )
            log.info { "Opprettet og ferdigstilte journalpost for barnebrillevedtak i joark for sakId: ${data.sakId}" }
        } catch (e: Throwable) {
            log.error(e) { "Klarte ikke å opprettet og ferdigstille journalpost for barnebrillevedtak i joark for sakId: ${data.sakId}" }
            throw e
        }
    }
}

@Hendelse("hm-opprettetOgFerdigstiltBarnebrillevedtakJournalpost")
internal data class JournalpostBarnebrillevedtakData(
    val fnr: String,
    val sakId: String,
    val dokumentTittel: String,
    val opprettet: LocalDateTime,
    val joarkRef: String? = null,
)

enum class Vedtaksstatus {
    INNVILGET,
    AVSLÅTT
}
