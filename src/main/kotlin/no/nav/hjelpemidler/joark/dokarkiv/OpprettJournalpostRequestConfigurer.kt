package no.nav.hjelpemidler.joark.dokarkiv

import no.nav.hjelpemidler.joark.dokarkiv.models.Dokument
import no.nav.hjelpemidler.joark.dokarkiv.models.DokumentVariant
import no.nav.hjelpemidler.joark.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.joark.dokarkiv.models.Sak
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.saf.enums.Kanal
import no.nav.hjelpemidler.saf.enums.Tema
import no.nav.hjelpemidler.saf.enums.Variantformat
import java.time.LocalDateTime

class OpprettJournalpostRequestConfigurer(
    val fnrBruker: String,
    val fnrAvsenderMottaker: String = fnrBruker,
    val dokumenttype: Dokumenttype,
    val journalposttype: OpprettJournalpostRequest.Journalposttype,
    val eksternReferanseId: String,
) {
    var tittel: String? = null
    var datoDokument: LocalDateTime? = null
    var datoMottatt: LocalDateTime? = null
    var journalførendeEnhet: String? = "9999"

    var opprettetAv: String? = null

    var dokumenter = mutableListOf<Dokument>()
        private set

    var sak: Sak? = null
        private set

    init {
        tittel = dokumenttype.tittel
    }

    fun dokument(
        fysiskDokument: ByteArray,
        dokumenttittel: String? = null,
    ) {
        dokumenter.add(
            Dokument(
                brevkode = dokumenttype.brevkode,
                dokumentvarianter = listOf(fysiskDokument.toArkiv()),
                tittel = dokumenttittel ?: dokumenttype.dokumenttittel,
            )
        )
    }

    fun hotsak(sakId: String) {
        sak = fagsakHjelpemidler(sakId)
    }

    fun optiker(sakId: String) {
        sak = fagsakBarnebriller(sakId)
    }

    fun optikerGenerellSak() {
        sak = generellSak()
    }

    operator fun invoke(): OpprettJournalpostRequest {
        check(dokumenter.isNotEmpty()) {
            "Ingen dokumenter er lagt til!"
        }
        return OpprettJournalpostRequest(
            avsenderMottaker = avsenderMottakerMedFnr(fnrAvsenderMottaker),
            bruker = brukerMedFnr(fnrBruker),
            datoDokument = datoDokument,
            datoMottatt = datoMottatt,
            dokumenter = dokumenter.toList(),
            eksternReferanseId = eksternReferanseId,
            journalfoerendeEnhet = journalførendeEnhet,
            journalposttype = journalposttype,
            kanal = Kanal.NAV_NO.toString(),
            sak = sak,
            tema = Tema.HJE.toString(),
            tittel = tittel,
        )
    }
}

private fun ByteArray.toArkiv(): DokumentVariant = DokumentVariant(
    filtype = "PDFA", fysiskDokument = this, variantformat = Variantformat.ARKIV.toString()
)
