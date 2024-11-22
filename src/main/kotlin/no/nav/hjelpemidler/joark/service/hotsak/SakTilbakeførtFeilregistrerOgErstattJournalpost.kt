package no.nav.hjelpemidler.joark.service.hotsak

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.domain.Sakstype
import no.nav.hjelpemidler.joark.jsonMapper
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
                    "prioritet",
                )
            }
        }.register(this)
    }

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val journalpostId = packet["joarkRef"].textValue()
        if (skip(journalpostId)) {
            log.warn {
                "Hopper over feilregistrering av journalpost med journalpostId: $journalpostId"
            }
            return
        }

        val data: MottattJournalpostData = jsonMapper.readValue(packet.toJson())

        log.info {
            "Journalpost til feilregistrering av sakstilknytning mottatt, sakId: ${data.sakId}, sakstype: ${data.sakstype}, journalpostId: $journalpostId"
        }
        try {
            journalpostService.feilregistrerSakstilknytning(journalpostId)
            log.info { "Feilregistrerte sakstilknytning for sakId: ${data.sakId}, journalpostId: $journalpostId" }
        } catch (e: Throwable) {
            log.error(e) { "Klarte ikke å feilregistrere sakstilknytning for journalpostId: $journalpostId, sakId: ${data.sakId}" }
            throw e
        }

        log.info { "Oppretter ny journalpost for feilregistrert sakstilknytning, sakId: ${data.sakId}, søknadId: ${data.soknadId}, sakstype: ${data.sakstype}, dokumenttittel: ${data.dokumentBeskrivelse}, journalpostId: $journalpostId" }
        val eksternReferanseId = "${data.soknadId}_${journalpostId}_HOTSAK_TIL_GOSYS"
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
            log.info { "Opprettet journalpost med status mottatt i dokarkiv for søknadId: ${data.soknadId}, journalpostId: $nyJournalpostId, sakId: ${data.sakId}, sakstype: ${data.sakstype}" }
        } catch (e: Throwable) {
            log.error(e) { "Klarte ikke å opprette journalpost med status mottatt i dokarkiv for søknadId: ${data.soknadId}, sakstype: ${data.sakstype}" }
            throw e
        }
    }
}

private fun skip(journalpostId: String): Boolean =
    journalpostId in setOf("535250492")

private data class MottattJournalpostData(
    val fnrBruker: String,
    val soknadId: UUID,
    @JsonAlias("joarkRef") val journalpostId: String,
    @JsonAlias("saksnummer") val sakId: String,
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
