package no.nav.hjelpemidler.joark.service.hotsak

import com.fasterxml.jackson.annotation.JsonAlias
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.collections.joinToString
import no.nav.hjelpemidler.joark.domain.Sakstype
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.kafka.KafkaEvent
import no.nav.hjelpemidler.kafka.KafkaMessage
import no.nav.hjelpemidler.rapids_and_rivers.publish
import no.nav.hjelpemidler.serialization.jackson.jsonToValue
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Sak fra Hotsak skal overføres til behandling i Gosys. Feilregistrer journalpost og lag kopi.
 * Det opprettes oppgaver for journalføring i hm-oppgave-sink downstream.
 */
class SakOverførtGosysFeilregistrerOgErstattJournalpost(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "hm-sakTilbakeførtGosys") }
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
        val kildeJournalpostId = packet["joarkRef"].textValue()
        val sakId = packet["saksnummer"].textValue()
        if (kildeJournalpostId in skipJournalpostId) {
            log.warn { "Hopper over feilregistrering av journalpost med journalpostId: $kildeJournalpostId, sakId: $sakId" }
            return
        }
        if (sakId in skipSakId) {
            log.warn { "Hopper over feilregistrering av journalpostId: $kildeJournalpostId for sakId: $sakId" }
            return
        }

        val data: MottattJournalpostData = jsonToValue(packet.toJson())

        log.info {
            data.toMap().joinToString(prefix = "Journalpost til feilregistrering av sakstilknytning mottatt, ")
        }
        try {
            journalpostService.feilregistrerSakstilknytning(kildeJournalpostId)
            log.info { data.toMap().joinToString(prefix = "Feilregistrerte sakstilknytning, ") }
        } catch (e: Throwable) {
            log.error(e) { data.toMap().joinToString(prefix = "Klarte ikke å feilregistrere sakstilknytning, ") }
            throw e
        }

        log.info {
            data.toMap().joinToString(prefix = "Oppretter ny journalpost etter feilregistrering av sakstilknytning, ")
        }
        val nyEksternReferanseId = "${data.søknadId}_${data.sakId}_${kildeJournalpostId}_HOTSAK_TIL_GOSYS"
        try {
            val nyJournalpostId = when (data.sakstype) {
                Sakstype.BARNEBRILLER, Sakstype.BESTILLING, Sakstype.SØKNAD -> journalpostService.kopierJournalpost(
                    kildeJournalpostId = kildeJournalpostId,
                    nyEksternReferanseId = nyEksternReferanseId
                )

                Sakstype.BYTTE, Sakstype.BRUKERPASSBYTTE -> error("Uventet sakstype: ${data.sakstype}")
            }
            if (nyJournalpostId == null) {
                log.warn { "Stopper behandling av kopiert journalpost fordi den allerede er behandlet, journalpostId: $kildeJournalpostId, eksternReferanseId: $nyEksternReferanseId" }
                return
            }

            // Oppdater journalpostId etter feilregistrering og ny-oppretting
            val nyData = data.copy(journalpostId = nyJournalpostId)

            context.publish(nyData.fnrBruker, nyData)
            log.info {
                nyData.toMap()
                    .plus(
                        mapOf(
                            "kildeJournalpostId" to kildeJournalpostId,
                            "nyEksternReferanseId" to nyEksternReferanseId,
                        )
                    )
                    .joinToString(prefix = "Opprettet ny journalpost med status mottatt etter feilregistrering, ")
            }
        } catch (e: Throwable) {
            log.error(e) {
                data.toMap()
                    .joinToString(prefix = "Klarte ikke å opprette ny journalpost med status mottatt etter feilregistrering, ")
            }
            throw e
        }
    }
}

private val skipJournalpostId = setOf<String>()
private val skipSakId = setOf<String>("206197")

@KafkaEvent("hm-opprettetMottattJournalpost")
private data class MottattJournalpostData(
    val fnrBruker: String,
    @JsonAlias("soknadId")
    val søknadId: UUID,
    val sakstype: Sakstype,
    val dokumentBeskrivelse: String,
    val enhet: String,
    val navIdent: String?,
    val valgteÅrsaker: Set<String>,
    val begrunnelse: String?,
    val prioritet: String?,

    @JsonAlias("joarkRef")
    val journalpostId: String,

    @JsonAlias("saksnummer")
    val sakId: String,

    override val eventId: UUID = UUID.randomUUID(),
) : KafkaMessage {
    val opprettet = LocalDateTime.now()

    @Deprecated("Bruk fnrBruker")
    val fodselNrBruker by this::fnrBruker

    @Deprecated("Bruk journalpostId")
    val joarkRef by this::journalpostId

    fun toMap(): Map<String, Any?> = mapOf(
        "sakId" to sakId,
        "søknadId" to søknadId,
        "journalpostId" to journalpostId,
        "sakstype" to sakstype,
        "dokumenttittel" to "'$dokumentBeskrivelse'",
    )
}
