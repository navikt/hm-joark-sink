package no.nav.hjelpemidler.joark.service

import no.nav.hjelpemidler.dokarkiv.avsenderMottakerMedFnr
import no.nav.hjelpemidler.dokarkiv.brukerMedFnr
import no.nav.hjelpemidler.dokarkiv.models.Dokument
import no.nav.hjelpemidler.dokarkiv.models.DokumentVariant
import no.nav.hjelpemidler.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.dokarkiv.models.Sak
import no.nav.hjelpemidler.saf.enums.Kanal
import no.nav.hjelpemidler.saf.enums.Tema
import no.nav.hjelpemidler.saf.enums.Variantformat
import java.time.LocalDateTime

class OpprettJournalpostRequestConfigurer(
    val fnrBruker: String,
    val fnrAvsender: String = fnrBruker,
    val navnAvsender: String,
    val journalposttype: OpprettJournalpostRequest.Journalposttype,
) {
    private var dokumenter = mutableListOf<Dokument>()
    fun dokument(
        fysiskDokument: ByteArray,
        dokumenttype: Dokumenttype? = null,
        dokumenttittel: String? = null,
    ) = dokumenter.add(
        Dokument(
            brevkode = dokumenttype?.brevkode,
            dokumentvarianter = listOf(
                DokumentVariant(
                    filtype = "PDFA",
                    fysiskDokument = fysiskDokument,
                    variantformat = Variantformat.ARKIV.toString()
                )
            ),
            tittel = dokumenttittel ?: dokumenttype?.dokumenttittel,
        )
    )

    private var sak: Sak? = null
    fun sakFraHotsak(sakId: String) {
        sak = Sak(sakId, Sak.Fagsaksystem.HJELPEMIDLER, Sak.Sakstype.FAGSAK)
    }

    fun sakFraOptiker(sakId: String) {
        sak = Sak(sakId, Sak.Fagsaksystem.BARNEBRILLER, Sak.Sakstype.FAGSAK)
    }

    private var tittel: String? = null
    fun tittelFra(dokumenttype: Dokumenttype?) {
        tittel = dokumenttype?.tittel
    }

    var eksternReferanseId: String? = null
    var datoDokument: LocalDateTime? = null
    var datoMottatt: LocalDateTime? = null
    var journalførendeEnhet: String? = "9999"

    operator fun invoke(): OpprettJournalpostRequest {
        check(dokumenter.isNotEmpty()) {
            "Ingen dokumenter er lagt til!"
        }
        return OpprettJournalpostRequest(
            avsenderMottaker = avsenderMottakerMedFnr(fnrAvsender, navnAvsender),
            bruker = brukerMedFnr(fnrBruker),
            journalposttype = journalposttype,
            dokumenter = dokumenter,
            sak = sak,
            tittel = tittel,
            eksternReferanseId = eksternReferanseId,
            datoDokument = datoDokument,
            datoMottatt = datoMottatt,
            tema = Tema.HJE.toString(),
            kanal = Kanal.NAV_NO.toString(),
            journalfoerendeEnhet = journalførendeEnhet,
        )
    }
}
