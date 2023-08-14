package no.nav.hjelpemidler.joark.pdf

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.Base64

class SøknadPdfGeneratorClientTest {
    private val pdf = Base64.getUrlEncoder().encode("foobar".toByteArray())
    private val client = SøknadPdfGeneratorClient(engine = MockEngine { request ->
        request.url.fullPath shouldBe "/api/v1/genpdf/hmb/hmb"
        respond(pdf, HttpStatusCode.OK)
    })

    @Test
    fun `genererer pdf`() = runTest {
        val response = client.genererPdfSøknad(
            """
                {
                    "soknad": "soknad"
                }
            """.trimIndent()
        )

        response shouldBe pdf
    }
}
