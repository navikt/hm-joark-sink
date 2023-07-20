package no.nav.hjelpemidler.joark.service

import no.nav.hjelpemidler.dokarkiv.avsenderMottakerMedFnr
import no.nav.hjelpemidler.dokarkiv.brukerMedFnr
import no.nav.hjelpemidler.dokarkiv.fagsakBarnebriller
import no.nav.hjelpemidler.dokarkiv.fagsakHjelpemidler
import no.nav.hjelpemidler.dokarkiv.models.Dokument
import no.nav.hjelpemidler.dokarkiv.models.DokumentVariant
import no.nav.hjelpemidler.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.dokarkiv.models.Sak
import no.nav.hjelpemidler.domain.Dokumenttype
import no.nav.hjelpemidler.saf.enums.Kanal
import no.nav.hjelpemidler.saf.enums.Tema
import no.nav.hjelpemidler.saf.enums.Variantformat
import java.time.LocalDateTime
import java.util.LinkedList

class OpprettJournalpostRequestConfigurer(
    val fnrBruker: String,
    val fnrAvsenderMottaker: String = fnrBruker,
    val dokumenttype: Dokumenttype,
    val journalposttype: OpprettJournalpostRequest.Journalposttype,
) {
    var tittel: String? = null
    var eksternReferanseId: String? = null
    var datoDokument: LocalDateTime? = null
    var datoMottatt: LocalDateTime? = null
    var journalførendeEnhet: String? = "9999"

    var dokumenter = LinkedList<Dokument>()
        private set

    var sak: Sak? = null
        private set

    init {
        tittel = dokumenttype.tittel
    }

    fun førsteside(fysiskDokument: ByteArray) {
        dokumenter.addFirst(
            Dokument(
                brevkode = dokumenttype.brevkode,
                dokumentvarianter = listOf(fysiskDokument.toArkiv()),
                tittel = dokumenttype.dokumenttittel,
            )
        )
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

    operator fun invoke(): OpprettJournalpostRequest {
        check(dokumenter.isNotEmpty()) {
            "Ingen dokumenter er lagt til!"
        }
        return OpprettJournalpostRequest(
            avsenderMottaker = avsenderMottakerMedFnr(fnrAvsenderMottaker),
            bruker = brukerMedFnr(fnrBruker),
            datoDokument = datoDokument,
            datoMottatt = datoMottatt,
            dokumenter = dokumenter,
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

private fun ByteArray.toArkiv(): DokumentVariant =
    DokumentVariant(
        filtype = "PDFA",
        fysiskDokument = this,
        variantformat = Variantformat.ARKIV.toString()
    )
