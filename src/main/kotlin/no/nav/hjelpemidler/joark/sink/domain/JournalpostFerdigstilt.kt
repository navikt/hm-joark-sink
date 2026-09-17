package no.nav.hjelpemidler.joark.sink.domain

import no.nav.hjelpemidler.domain.enhet.Enhetsnummer
import no.nav.hjelpemidler.domain.joark.JournalpostSak
import no.nav.hjelpemidler.domain.person.Fødselsnummer

data class JournalpostFerdigstilt(
    val journalpostId: String,
    val nyJournalpostId: String,
    val hoveddokumentTittel: String?,
    val fnrBruker: Fødselsnummer,
    val sak: JournalpostSak,
    val journalførendeEnhet: Enhetsnummer,
) {
    override fun toString(): String = if (journalpostId == nyJournalpostId) {
        "journalpostId: $journalpostId, journalførendeEnhet: $journalførendeEnhet, $sak"
    } else {
        "journalpostId: $journalpostId, nyJournalpostId: $nyJournalpostId, journalførendeEnhet: $journalførendeEnhet, $sak"
    }
}
