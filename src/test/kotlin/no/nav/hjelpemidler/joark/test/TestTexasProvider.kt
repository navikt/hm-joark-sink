package no.nav.hjelpemidler.joark.test

import io.ktor.client.engine.HttpClientEngine
import no.nav.hjelpemidler.http.openid.Texas
import no.nav.hjelpemidler.http.openid.TexasProvider
import no.nav.hjelpemidler.http.openid.TokenSet
import no.nav.hjelpemidler.http.openid.asTexas
import no.nav.hjelpemidler.service.LoadOrder
import kotlin.time.Duration.Companion.hours

@LoadOrder(-10)
class TestTexasProvider : TexasProvider {
    override fun invoke(engine: HttpClientEngine): Texas = TokenSet("token", 1.hours).asTexas()
}
