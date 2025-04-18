package no.nav.hjelpemidler.joark.dokarkiv

import com.fasterxml.jackson.databind.JsonNode
import no.nav.hjelpemidler.joark.dokarkiv.models.Dokument
import no.nav.hjelpemidler.joark.dokarkiv.models.DokumentVariant
import no.nav.hjelpemidler.joark.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.joark.dokarkiv.models.Sak
import no.nav.hjelpemidler.joark.dokarkiv.models.Tilleggsopplysning
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.saf.enums.Kanal
import no.nav.hjelpemidler.saf.enums.Tema
import no.nav.hjelpemidler.saf.enums.Variantformat
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import java.time.LocalDateTime

class OpprettJournalpostRequestConfigurer(
    val fnrBruker: String,
    val fnrAvsenderMottaker: String? = fnrBruker,
    val dokumenttype: Dokumenttype,
    val journalposttype: OpprettJournalpostRequest.Journalposttype,
    val eksternReferanseId: String,
) {
    var tittel: String? = null
    var datoMottatt: LocalDateTime? = null
    var journalførendeEnhet: String? = "9999"
    var kanal: String? = Kanal.NAV_NO.toString()

    var opprettetAv: String? = null

    init {
        tittel = dokumenttype.tittel
    }

    private val dokumenter = mutableListOf<Dokument>()
    fun dokument(
        fysiskDokument: ByteArray,
        dokumenttittel: String? = null,
        strukturertDokument: JsonNode? = null,
    ) {
        val varianter: MutableList<DokumentVariant> = mutableListOf(fysiskDokument.toArkiv())
        if (strukturertDokument != null) {
            varianter.add(strukturertDokument.toOriginal())
        }
        dokumenter.add(
            Dokument(
                brevkode = dokumenttype.brevkode,
                dokumentvarianter = varianter,
                tittel = dokumenttittel ?: dokumenttype.dokumenttittel,
            )
        )
    }

    private var sak: Sak? = null

    fun hotsak(sakId: String) {
        sak = fagsakHjelpemidler(sakId)
    }

    fun optikerFagsak(sakId: String) {
        sak = fagsakBarnebriller(sakId)
    }

    fun optikerGenerellSak() {
        sak = generellSak()
    }

    private var tilleggsopplysninger: List<Tilleggsopplysning>? = null
    fun tilleggsopplysninger(vararg tilleggsopplysninger: Pair<String, String?>, prefix: String? = null) {
        this.tilleggsopplysninger = tilleggsopplysninger.mapNotNull { (nøkkel, verdi) ->
            if (verdi == null) {
                null
            } else {
                Tilleggsopplysning(listOfNotNull(prefix, nøkkel).joinToString("_"), verdi)
            }
        }
    }

    operator fun invoke(): OpprettJournalpostRequest {
        check(dokumenter.isNotEmpty()) {
            "Ingen dokumenter er lagt til!"
        }
        return OpprettJournalpostRequest(
            dokumenter = dokumenter.toList(),
            eksternReferanseId = eksternReferanseId,
            journalposttype = journalposttype,
            avsenderMottaker = fnrAvsenderMottaker?.let { avsenderMottakerMedFnr(it) },
            bruker = brukerMedFnr(fnrBruker),
            datoMottatt = datoMottatt,
            journalfoerendeEnhet = journalførendeEnhet,
            kanal = kanal,
            sak = sak,
            tema = Tema.HJE.toString(),
            tilleggsopplysninger = tilleggsopplysninger,
            tittel = tittel,
        )
    }
}

private fun ByteArray.toArkiv(): DokumentVariant = DokumentVariant(
    filtype = "PDFA", fysiskDokument = this, variantformat = Variantformat.ARKIV.toString()
)

private fun JsonNode.toOriginal(): DokumentVariant = DokumentVariant(
    filtype = "JSON",
    fysiskDokument = jsonMapper.writeValueAsString(this).toByteArray(),
    variantformat = Variantformat.ORIGINAL.toString()
)
