package no.nav.hjelpemidler.joark.sink.saf

import com.expediagroup.graphql.client.types.GraphQLClientResponse
import no.nav.hjelpemidler.collections.enumSetOf
import no.nav.hjelpemidler.joark.sink.saf.enums.Kanal
import no.nav.hjelpemidler.joark.sink.saf.hentjournalpost.Journalpost

fun <T> GraphQLClientResponse<T>.resultOrThrow(): T {
    val errors = this.errors
    val data = this.data
    return when {
        errors != null -> error("Feil fra GraphQL-tjeneste: '${errors.joinToString { it.message }}'")
        data != null -> data
        else -> error("Både data og errors var null!")
    }
}

val Journalpost.isKanalDigital: Boolean get() = kanal in DIGITALE_KANALER
private val DIGITALE_KANALER = enumSetOf(
    Kanal.ALTINN,
    Kanal.EESSI,
    Kanal.NAV_NO,
    Kanal.NAV_NO_CHAT,
)
