package no.nav.hjelpemidler.joark.test

import io.ktor.http.ParametersBuilder
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.TokenSet
import kotlin.time.Duration.Companion.hours

class TestOpenIDClient(private val tokenSet: TokenSet = TokenSet.bearer(1.hours, "token")) : OpenIDClient {
    override suspend fun grant(builder: ParametersBuilder.() -> Unit): TokenSet =
        tokenSet
}
