package no.nav.hjelpemidler.saf

import com.expediagroup.graphql.client.types.GraphQLClientResponse

fun <T> GraphQLClientResponse<T>.resultOrThrow(): T {
    val errors = this.errors
    val data = this.data
    return when {
        errors != null -> error("Feil fra GraphQL-tjeneste: '${errors.joinToString { it.message }}'")
        data != null -> data
        else -> error("Både data og errors var null!")
    }
}
