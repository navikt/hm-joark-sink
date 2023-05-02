package no.nav.hjelpemidler.joark.service

import no.nav.hjelpemidler.dokarkiv.models.AvsenderMottaker
import no.nav.hjelpemidler.dokarkiv.models.Bruker
import no.nav.hjelpemidler.dokarkiv.models.Dokument
import no.nav.hjelpemidler.dokarkiv.models.DokumentVariant
import no.nav.hjelpemidler.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.dokarkiv.models.Sak

object OpprettJournalpostRequestFactory {
    fun foo(
        fnrBruker: String,
        navnBruker: String,
        sakId: String,
        tittel: String,
        pdf: ByteArray,
    ): OpprettJournalpostRequest {
        return OpprettJournalpostRequest(
            avsenderMottaker = AvsenderMottaker(fnrBruker, AvsenderMottaker.IdType.FNR, navnBruker),
            bruker = Bruker(fnrBruker, Bruker.IdType.FNR),
            dokumenter = listOf(
                Dokument(
                    brevkode = "vedtaksbrev_barnebriller",
                    dokumentvarianter = listOf(
                        DokumentVariant(
                            filtype = "PDFA",
                            variantformat = "ARKIV",
                            fysiskDokument = emptyList() //  Base64.getEncoder().encodeToString(pdf), fixme
                        )
                    ),
                    tittel = tittel
                )
            ),
            tema = "HJE",
            tittel = "Vedtak for barnebrille",
            kanal = "NAV_NO",
            eksternReferanseId = sakId + "BARNEBRILLEVEDTAK",
            journalposttype = OpprettJournalpostRequest.Journalposttype.UTGAAENDE,
            journalfoerendeEnhet = "9999",
            sak = Sak(
                fagsakId = sakId,
                fagsaksystem = Sak.Fagsaksystem.BARNEBRILLER,
                sakstype = Sak.Sakstype.FAGSAK
            )
        )
    }
}
