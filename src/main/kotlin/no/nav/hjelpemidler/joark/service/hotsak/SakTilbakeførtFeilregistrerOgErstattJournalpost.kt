package no.nav.hjelpemidler.joark.service.hotsak

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.domain.Sakstype
import no.nav.hjelpemidler.joark.publish
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

class SakTilbakeførtFeilregistrerOgErstattJournalpost(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-sakTilbakeførtGosys") }
            validate {
                it.requireKey(
                    "saksnummer",
                    "joarkRef",
                    "fnrBruker",
                    "dokumentBeskrivelse",
                    "soknadId",
                )
                it.interestedIn(
                    "sakstype",
                    "navIdent",
                    "valgteÅrsaker",
                    "enhet",
                    "begrunnelse",
                    "soknadJson",
                    "prioritet",
                )
            }
        }.register(this)
    }

    private val JsonMessage.sakId get() = this["saksnummer"].textValue()
    private val JsonMessage.journalpostId get() = this["joarkRef"].textValue()
    private val JsonMessage.fnrBruker get() = this["fnrBruker"].textValue()
    private val JsonMessage.dokumentBeskrivelse get() = this["dokumentBeskrivelse"].textValue()
    private val JsonMessage.søknadId get() = this["soknadId"].textValue()
    private val JsonMessage.sakstype get() = this["sakstype"].textValue()
    private val JsonMessage.navIdent get() = this["navIdent"].textValue()
    private val JsonMessage.valgteÅrsaker: Set<String> get() = this["valgteÅrsaker"].map { it.textValue() }.toSet()
    private val JsonMessage.enhet get() = this["enhet"].textValue()
    private val JsonMessage.begrunnelse get() = this["begrunnelse"].textValue()
    private val JsonMessage.prioritet: String? get() = this["prioritet"].textValue()

    @Deprecated("Vi skal slutte å sende dette feltet")
    private val JsonMessage.søknadJson get() = this["soknadJson"] // fixme -> slettes når vi ikke trenger dette feltet lenger

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val journalpostId = packet.journalpostId
        if (skip(journalpostId)) {
            log.warn {
                "Hopper over feilregistrering av journalpost med journalpostId: $journalpostId"
            }
            return
        }

        val søknadId = packet.søknadId
        val data = MottattJournalpostData(
            fnrBruker = packet.fnrBruker,
            soknadJson = packet.søknadJson,
            soknadId = UUID.fromString(søknadId),
            journalpostId = packet.journalpostId,
            sakId = packet.sakId,
            sakstype =  Sakstype.valueOf(packet.sakstype),
            dokumentBeskrivelse = packet.dokumentBeskrivelse,
            enhet = packet.enhet,
            navIdent = packet.navIdent,
            valgteÅrsaker = packet.valgteÅrsaker,
            begrunnelse = packet.begrunnelse,
            prioritet = packet.prioritet,
        )

        log.info {
            "Journalpost til feilregistrering av sakstilknytning mottatt, sakId: ${packet.sakId}, sakstype: ${packet.sakstype}, journalpostId: $journalpostId"
        }
        try {
            journalpostService.feilregistrerSakstilknytning(journalpostId)
            log.info { "Feilregistrerte sakstilknytning for sakId: ${packet.sakId}, journalpostId: $journalpostId" }
        } catch (e: Throwable) {
            log.error(e) { "Klarte ikke å feilregistrere sakstilknytning for journalpostId: $journalpostId, sakId: ${packet.sakId}" }
            throw e
        }

        log.info { "Oppretter ny journalpost for feilregistrert sakstilknytning, sakId: ${data.sakId}, søknadId: $søknadId, sakstype: ${data.sakstype}, dokumenttittel: ${data.dokumentBeskrivelse}, journalpostId: $journalpostId" }
        val eksternReferanseId = "${søknadId}_${journalpostId}_HOTSAK_TIL_GOSYS"
        try {
            val nyJournalpostId = when (data.sakstype) {
                Sakstype.BESTILLING, Sakstype.SØKNAD -> journalpostService.arkiverBehovsmelding(
                    fnrBruker = data.fnrBruker,
                    behovsmeldingId = data.soknadId,
                    sakstype = data.sakstype,
                    dokumenttittel = data.dokumentBeskrivelse,
                    eksternReferanseId = eksternReferanseId,
                )

                Sakstype.BARNEBRILLER -> journalpostService.kopierJournalpost(
                    journalpostId = journalpostId,
                    nyEksternReferanseId = eksternReferanseId
                )

                Sakstype.BYTTE, Sakstype.BRUKERPASSBYTTE -> throw IllegalArgumentException("Uventet sakstype: ${data.sakstype}")
            }

            // Oppdater journalpostId etter feilregistrering og ny-oppretting
            val data = data.copy(journalpostId = nyJournalpostId)

            context.publish(data.fnrBruker, data)
            log.info { "Opprettet journalpost med status mottatt i dokarkiv for søknadId: $søknadId, journalpostId: $nyJournalpostId, sakId: ${data.sakId}, sakstype: ${data.sakstype}" }
        } catch (e: Throwable) {
            log.error(e) { "Klarte ikke å opprette journalpost med status mottatt i dokarkiv for søknadId: $søknadId, sakstype: ${data.sakstype}" }
            throw e
        }
    }
}

private fun skip(journalpostId: String): Boolean =
    journalpostId in setOf("535250492")

internal data class MottattJournalpostData(
    val fnrBruker: String,
    val soknadId: UUID,
    val soknadJson: JsonNode,
    val journalpostId: String,
    val sakId: String,
    val sakstype: Sakstype,
    val dokumentBeskrivelse: String,
    val enhet: String,
    val navIdent: String?,
    val valgteÅrsaker: Set<String>,
    val begrunnelse: String?,
    val prioritet: String?,
) {
    val eventId = UUID.randomUUID()
    val eventName = "hm-opprettetMottattJournalpost"
    val opprettet = LocalDateTime.now()

    var fodselNrBruker = fnrBruker // @deprecated
    val joarkRef = journalpostId // @deprecated
}
