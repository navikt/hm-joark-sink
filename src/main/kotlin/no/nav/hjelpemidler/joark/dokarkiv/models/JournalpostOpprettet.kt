package no.nav.hjelpemidler.joark.dokarkiv.models

data class JournalpostOpprettet(
    val journalpostId: String,
    val dokumentIder: Set<String>,
    val ferdigstilt: Boolean,
) {
    constructor(response: OpprettJournalpostResponse) : this(
        journalpostId = response.journalpostId,
        dokumentIder = response.dokumenter?.mapNotNull(DokumentInfoId::dokumentInfoId)?.toSet() ?: emptySet(),
        ferdigstilt = response.journalpostferdigstilt,
    )
}
