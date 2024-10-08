package no.nav.hjelpemidler.joark.service.barnebriller

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService

private val log = KotlinLogging.logger {}

/**
 * Barnebrillevedtak er opprettet i optikerløsningen
 */
class OpprettOgFerdigstillJournalpostBarnebriller(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-barnebrillevedtak-opprettet") }
            validate {
                it.requireKey(
                    "fnr",
                    "brukersNavn",
                    "orgnr",
                    "orgNavn",
                    "orgAdresse",
                    "navnAvsender",
                    "eventId",
                    "opprettetDato",
                    "sakId",
                    "brilleseddel",
                    "bestillingsdato",
                    "bestillingsreferanse",
                    "satsBeskrivelse",
                    "satsBeløp",
                    "beløp"
                )
            }
        }.register(this)
    }

    private val JsonMessage.fnr get() = this["fnr"].textValue()
    private val JsonMessage.brukersNavn get() = this["brukersNavn"].textValue()
    private val JsonMessage.orgnr get() = this["orgnr"].textValue()
    private val JsonMessage.orgNavn get() = this["orgNavn"].textValue()
    private val JsonMessage.orgAdresse get() = this["orgAdresse"].textValue()
    private val JsonMessage.navnAvsender get() = this["navnAvsender"].textValue()
    private val JsonMessage.opprettet get() = this["opprettetDato"].asLocalDateTime()
    private val JsonMessage.sakId get() = this["sakId"].textValue()
    private val JsonMessage.brilleseddel get() = this["brilleseddel"]
    private val JsonMessage.bestillingsdato get() = this["bestillingsdato"].asLocalDate()
    private val JsonMessage.bestillingsreferanse get() = this["bestillingsreferanse"].textValue()
    private val JsonMessage.satsBeskrivelse get() = this["satsBeskrivelse"].textValue()
    private val JsonMessage.satsBeløp get() = this["satsBeløp"].decimalValue()
    private val JsonMessage.beløp get() = this["beløp"].decimalValue()

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val data = JournalpostBarnebrillevedtakData(
            fnr = packet.fnr,
            brukersNavn = packet.brukersNavn,
            orgnr = packet.orgnr,
            orgNavn = packet.orgNavn,
            orgAdresse = packet.orgAdresse,
            sakId = packet.sakId,
            navnAvsender = packet.navnAvsender,
            brilleseddel = packet.brilleseddel,
            opprettet = packet.opprettet,
            bestillingsdato = packet.bestillingsdato,
            bestillingsår = packet.bestillingsdato.year,
            bestillingsreferanse = packet.bestillingsreferanse,
            satsBeskrivelse = packet.satsBeskrivelse,
            satsBeløp = packet.satsBeløp,
            beløp = packet.beløp
        )
        log.info { "Sak til journalføring barnebriller mottatt, sakId: ${data.sakId}" }

        try {
            val fysiskDokument = journalpostService.genererPdf(data)
            val journalpost = journalpostService.opprettInngåendeJournalpost(
                fnrAvsender = data.fnr,
                dokumenttype = data.dokumenttype,
                eksternReferanseId = "${data.sakId}BARNEBRILLEAPI",
                forsøkFerdigstill = true,
            ) {
                dokument(fysiskDokument = fysiskDokument)
                optiker(data.sakId)
                datoMottatt = data.opprettet
            }

            context.publish(
                data.fnr,
                data.toJson(
                    journalpost.journalpostId,
                    journalpost.dokumenter?.mapNotNull { it.dokumentInfoId } ?: listOf(),
                    "hm-opprettetOgFerdigstiltBarnebrillerJournalpost",
                )
            )
            log.info { "Opprettet og ferdigstilte journalpost for barnebriller i joark for sakId: ${data.sakId}" }
        } catch (e: Throwable) {
            log.error(e) { "Kunne ikke opprette og ferdigstille journalpost for barnebriller, sakId: ${data.sakId}" }
            throw e
        }
    }
}
