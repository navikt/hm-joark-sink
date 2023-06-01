package no.nav.hjelpemidler.saf

import com.expediagroup.graphql.client.types.GraphQLClientResponse
import no.nav.hjelpemidler.saf.hentjournalpost.Journalpost

fun <T> GraphQLClientResponse<T>.resultOrThrow(): T {
    val errors = this.errors
    val data = this.data
    return when {
        errors != null -> error("Feil fra GraphQL-tjeneste: '${errors.joinToString { it.message }}'")
        data != null -> data
        else -> error("Både data og errors var null!")
    }
}

fun Journalpost?.tekst(): String =
    when {
        this == null -> "null"
        else -> buildString {
            val dokument = dokumenter?.filterNotNull()?.firstOrNull()
            append("journalpostId: ")
            append(journalpostId)
            append(", eksternReferanseId: '")
            append(eksternReferanseId)
            append("', tittel: '")
            append(tittel)
            append("', type: ")
            append(journalposttype)
            append(", status: ")
            append(journalstatus)
            append(", kanal: ")
            append(kanal)
            append(", tema: ")
            append(tema)
            append(", behandlingstema: '")
            append(behandlingstema)
            append("', opprettet: '")
            append(datoOpprettet)
            append("', fagsakId: '")
            append(sak?.fagsakId)
            append("', fagsaksystem: '")
            append(sak?.fagsaksystem)
            append("', fagsakOpprettet: '")
            append(sak?.datoOpprettet)
            append("', journalførendeEnhet: '")
            append(journalfoerendeEnhet)
            append("', brevkode: '")
            append(dokument?.brevkode)
            append("', dokumenttittel: '")
            append(dokument?.tittel)
            append("'")
        }
    }
