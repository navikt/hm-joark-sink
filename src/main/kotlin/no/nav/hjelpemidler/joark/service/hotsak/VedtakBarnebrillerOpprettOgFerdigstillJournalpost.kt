package no.nav.hjelpemidler.joark.service.hotsak

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
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
            validate { it.demandValue("eventName", "hm-manuelt-barnebrillevedtak-opprettet") }
            validate {
                it.requireKey(
                    "saksnummer",
                    "fnrBruker",
                    "navnBruker",
                    "navnAvsender",
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
    private val JsonMessage.navnBruker get() = this["navnBruker"].textValue()
    private val JsonMessage.navnAvsender get() = this["navnAvsender"].textValue()
    private val JsonMessage.opprettet get() = this["opprettet"].asLocalDateTime()
    private val JsonMessage.sakId get() = this["saksnummer"].textValue()
    private val JsonMessage.fysiskDokument get() = this["pdf"].binaryValue()
    private val JsonMessage.vedtaksstatus get() = this["vedtaksstatus"].textValue()?.let(Vedtaksstatus::valueOf)
    private val JsonMessage.opprettetAv: String? get() = this["opprettetAv"].textValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val sakId = packet.sakId
        val data = JournalpostBarnebrillevedtakData(
            fnr = packet.fnrBruker,
            brukersNavn = packet.navnBruker,
            sakId = sakId,
            navnAvsender = packet.navnAvsender,
            dokumentTittel = "Journalføring barnebrillevedtak",
            opprettet = packet.opprettet,
            pdf = packet.fysiskDokument
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
                dokument(fysiskDokument = data.pdf)
                hotsak(sakId)
                opprettetAv = packet.opprettetAv
            }

            context.publish(
                data.fnr,
                data.toJson(
                    journalpostId,
                    "hm-opprettetOgFerdigstiltBarnebrillevedtakJournalpost"
                )
            )
            log.info("Opprettet og ferdigstilte journalpost for barnebrillevedtak i joark for sakId: ${data.sakId}")
        } catch (e: Throwable) {
            log.error(e) { "Klarte ikke å opprettet og ferdigstille journalpost for barnebrillevedtak i joark for sakId: ${data.sakId}" }
            throw e
        }
    }
}

private data class JournalpostBarnebrillevedtakData(
    val fnr: String,
    val brukersNavn: String,
    val sakId: String,
    val navnAvsender: String,
    val dokumentTittel: String,
    val opprettet: LocalDateTime,
    val pdf: ByteArray,
) {
    @Deprecated("Bruk Jackson direkte")
    fun toJson(journalpostId: String, eventName: String): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["fnr"] = this.fnr
            it["eventName"] = eventName
            it["opprettet"] = opprettet
            it["joarkRef"] = journalpostId
            it["sakId"] = this.sakId
            it["dokumentTittel"] = this.dokumentTittel
            it["eventId"] = UUID.randomUUID()
        }.toJson()
    }
}

enum class Vedtaksstatus {
    INNVILGET,
    AVSLÅTT
}
