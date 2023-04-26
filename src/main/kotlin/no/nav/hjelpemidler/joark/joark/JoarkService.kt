package no.nav.hjelpemidler.joark.joark

import io.ktor.util.encodeBase64
import no.nav.hjelpemidler.joark.joark.model.AvsenderMottaker
import no.nav.hjelpemidler.joark.joark.model.Bruker
import no.nav.hjelpemidler.joark.joark.model.Dokumenter
import no.nav.hjelpemidler.joark.joark.model.Dokumentvarianter
import no.nav.hjelpemidler.saf.SafClient

class JoarkService(private val joarkClient: JoarkClientV4, private val safClient: SafClient) {

    suspend fun feilregistrerJournalpost(journalpostId: String) {
        joarkClient.feilregistrerJournalpost(journalpostId)
    }

    suspend fun kopierJournalpost(journalpostId: String) {
        val journalpost = checkNotNull(safClient.hentJournalpost(journalpostId)) {
            "Fant ikke journalpost med journalpostId: $journalpostId"
        }

        val fysiskeDokumenter = journalpost.dokumenter?.filterNotNull()
            ?.map { dokument ->
                val dokumentInfoId = dokument.dokumentInfoId
                val dokumentvarianter = dokument.dokumentvarianter.filterNotNull().map {
                    val fysiskDokument = safClient.hentDokument(journalpostId, dokumentInfoId, it.variantformat)
                    Dokumentvarianter(
                        filnavn = it.filnavn!!,
                        filtype = it.filtype!!,
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
        val avsenderMottaker = checkNotNull(journalpost.avsenderMottaker)
        val bruker = checkNotNull(journalpost.bruker)

        val opprettJournalpostRequest = OpprettJournalpostRequest(
            avsenderMottaker = AvsenderMottaker(
                id = avsenderMottaker.id!!,
                idType = avsenderMottaker.type.toString(),
                land = avsenderMottaker.land,
                navn = avsenderMottaker.navn
            ),
            bruker = Bruker(
                id = bruker.id!!,
                idType = bruker.type.toString()
            ),
            datoMottatt = journalpost.datoOpprettet,
            dokumenter = fysiskeDokumenter,
            tema = journalpost.tema.toString(),
            tittel = journalpost.tittel.toString(),
            kanal = journalpost.kanal.toString(),
            eksternReferanseId = "", // FIXME
            journalpostType = journalpost.journalposttype.toString()
        )

        // TODO bruke joarkClient for å opprette journalpost
    }
}
