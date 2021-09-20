package no.nav.hjelpemidler.joark.joark.model

data class HjelpemidlerDigitalSoknad(
    val avsenderMottaker: AvsenderMottaker,
    val bruker: Bruker,
    val dokumenter: List<Dokumenter>?,
    val tema: String,
    val tittel: String,
    val kanal: String,
    val eksternReferanseId: String,
    val journalpostType: String
)

data class MidlertidigJournalForing(
    val avsenderMottaker: AvsenderMottaker,
    val bruker: Bruker,
    val dokumenter: List<Dokumenter>?,
    val tema: String,
    val tittel: String,
    val kanal: String,
    val eksternReferanseId: String,
    val journalpostType: String
)

data class AvsenderMottaker(
    val id: String,
    val idType: String,
    val land: String, // TODO: Denne skal nok ikkje setjast
    val navn: String
)

data class Bruker(
    val id: String,
    val idType: String
)

data class Dokumentvarianter(
    val filnavn: String,
    val filtype: String,
    val variantformat: String,
    val fysiskDokument: String
)

data class Dokumenter(
    val brevkode: String,
    val dokumentKategori: String,
    val dokumentvarianter: List<Dokumentvarianter>,
    val tittel: String
)

// Response

data class OpprettJournalpostResponse(
    val journalpostId: String,
    val journalpostferdigstilt: Boolean?,
    val journalstatus: String?,
    val melding: String?,
    val dokumenter: List<DokumentInfo>?

)

data class DokumentInfo(
    val brevkode: String?,
    val dokumentInfoId: String?,
    val tittel: String?
)
