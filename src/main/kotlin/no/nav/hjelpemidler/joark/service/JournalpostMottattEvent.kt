package no.nav.hjelpemidler.joark.service

import no.nav.hjelpemidler.saf.enums.Kanal
import no.nav.hjelpemidler.saf.enums.Tema
import no.nav.hjelpemidler.saf.enums.Variantformat
import java.time.LocalDateTime
import java.util.UUID

/**
 * https://github.com/navikt/hm-joark-listener/blob/6ead58ce6475525dd19ceeadafdea6cd62970f5d/src/main/kotlin/no/nav/hjelpemidler/joark/JournalpostMottattRiver.kt#LL284C25-L284C25
 */
data class JournalpostMottattEvent(
    val eventId: UUID = UUID.randomUUID(),
    val eventName: String = "hm-journalpost-mottatt-overført",
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val fnr: String,
    val journalpostId: String,
    val mottattJournalpost: MottattJournalpost,
)

data class MottattJournalpost(
    val tema: Tema,
    val journalpostOpprettet: LocalDateTime,
    val opprettetAvEnhet: String = "9999",
    val journalførendeEnhet: String?,
    val tittel: String?,
    val kanal: Kanal?,
    val dokumenter: List<Dokument> = emptyList(),
)

data class Dokument(
    val dokumentId: String,
    val tittel: String?,
    val brevkode: String?,
    val skjerming: String?,
    val vedlegg: List<Vedlegg> = emptyList(),
    val varianter: List<Variant> = emptyList(),
)

data class Vedlegg(
    val vedleggId: String,
    val tittel: String?,
)

data class Variant(
    val format: Variantformat,
    val skjerming: String?,
)
