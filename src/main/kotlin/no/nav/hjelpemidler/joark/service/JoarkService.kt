package no.nav.hjelpemidler.joark.service

import io.ktor.util.encodeBase64
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.hjelpemidler.joark.joark.JoarkClientV4
import no.nav.hjelpemidler.joark.joark.OpprettJournalpostRequest
import no.nav.hjelpemidler.joark.joark.model.AvsenderMottaker
import no.nav.hjelpemidler.joark.joark.model.Bruker
import no.nav.hjelpemidler.joark.joark.model.Dokumenter
import no.nav.hjelpemidler.joark.joark.model.Dokumentvarianter
import no.nav.hjelpemidler.saf.SafClient
import java.util.UUID

private val log = KotlinLogging.logger {}

class JoarkService(
    private val joarkClient: JoarkClientV4,
    private val safClient: SafClient,
) {
    suspend fun feilregistrerJournalpost(journalpostId: String): String =
        withLoggingContext("journalpostId" to journalpostId) {
            log.info {
                "Feilregistrerer journalpost med journalpostId: $journalpostId"
            }
            joarkClient.feilregistrerJournalpost(journalpostId)
            journalpostId
        }

    suspend fun kopierJournalpost(søknadId: UUID, journalpostId: String): String =
        withLoggingContext("journalpostId" to journalpostId) {
            log.info {
                "Kopierer journalpost med journalpostId: $journalpostId"
            }

            val journalpost = checkNotNull(safClient.hentJournalpost(journalpostId)) {
                "Fant ikke journalpost med journalpostId: $journalpostId"
            }

            val fysiskeDokumenter = journalpost.dokumenter?.filterNotNull()
                ?.map { dokument ->
                    val dokumentInfoId = dokument.dokumentInfoId
                    val dokumentvarianter = dokument.dokumentvarianter.filterNotNull().map {
                        val fysiskDokument = safClient.hentDokument(journalpostId, dokumentInfoId, it.variantformat)
                        Dokumentvarianter(
                            filnavn = checkNotNull(it.filnavn),
                            filtype = checkNotNull(it.filtype),
                            variantformat = it.variantformat.toString(),
                            fysiskDokument = fysiskDokument.encodeBase64()
                        )
                    }
                    Dokumenter(
                        brevkode = dokument.brevkode,
                        dokumentvarianter = dokumentvarianter,
                        tittel = dokument.tittel!!
                    )
                }

            val avsenderMottaker = checkNotNull(journalpost.avsenderMottaker) {
                "journalpost.avsenderMottaker var null, journalpostId: $journalpostId"
            }
            val bruker = checkNotNull(journalpost.bruker) {
                "journalpost.bruker var null, journalpostId: $journalpostId"
            }

            val opprettJournalpostRequest = OpprettJournalpostRequest(
                avsenderMottaker = AvsenderMottaker(
                    id = checkNotNull(avsenderMottaker.id),
                    idType = avsenderMottaker.type.toString(),
                    land = avsenderMottaker.land,
                    navn = avsenderMottaker.navn
                ),
                bruker = Bruker(
                    id = checkNotNull(bruker.id),
                    idType = bruker.type.toString()
                ),
                datoMottatt = journalpost.datoOpprettet,
                dokumenter = fysiskeDokumenter,
                tema = journalpost.tema.toString(),
                tittel = journalpost.tittel.toString(),
                kanal = journalpost.kanal.toString(),
                eksternReferanseId = søknadId.toString() + "HOTSAK_TIL_GOSYS",
                journalpostType = journalpost.journalposttype.toString()
            )

            val opprettJournalpostResponse = joarkClient.opprettJournalpost(opprettJournalpostRequest)
            val nyJournalpostId = opprettJournalpostResponse.journalpostId
            log.info {
                "Kopierte journalpost med journalpostId: $journalpostId, nyJournalpostId: $nyJournalpostId"
            }
            nyJournalpostId
        }
}
