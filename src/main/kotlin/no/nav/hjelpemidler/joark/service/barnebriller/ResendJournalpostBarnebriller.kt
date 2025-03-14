package no.nav.hjelpemidler.joark.service.barnebriller

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.publish
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.serialization.jackson.jsonMapper

private val log = KotlinLogging.logger {}

class ResendJournalpostBarnebriller(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("eventName", "hm-barnebrillevedtak-rekjor") }
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

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        val data: JournalpostBarnebrillevedtakData = jsonMapper.readValue(packet.toJson())
        log.info { "Sak til rejournalføring barnebriller mottatt, sakId: ${data.sakId}" }

        try {
            val fysiskDokument = journalpostService.genererPdf(data)
            val journalpost = journalpostService.opprettInngåendeJournalpost(
                fnrAvsender = data.fnr,
                dokumenttype = data.dokumenttype,
                eksternReferanseId = "RE_${data.sakId}BARNEBRILLEAPI",
                forsøkFerdigstill = true,
            ) {
                dokument(fysiskDokument = fysiskDokument)
                optiker(data.sakId)
                datoMottatt = data.opprettet
            }

            context.publish(
                data.fnr,
                data.tilUtgående(
                    journalpost.journalpostId,
                    journalpost.dokumentIder,
                )
            )
            log.info { "Opprettet og ferdigstilte journalpost for barnebriller i joark for sakId: ${data.sakId}" }
        } catch (e: Throwable) {
            log.error(e) { "Kunne ikke opprette og ferdigstille journalpost for barnebriller, sakId: ${data.sakId}" }
            throw e
        }
    }
}
