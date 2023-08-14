package no.nav.hjelpemidler.saf

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.fullPath
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.joark.test.TestOpenIDClient
import no.nav.hjelpemidler.saf.enums.Variantformat
import kotlin.test.Test

class SafClientTest {
    @Test
    fun `henter dokument`() = runTest {
        val client = SafClient(azureADClient = TestOpenIDClient(), engine = MockEngine { request ->
            request.url.fullPath shouldBe "/rest/hentdokument/1/2/ARKIV"
            respond(ByteArray(0))
        })
        client.hentDokument("1", "2", Variantformat.ARKIV)
    }
}
