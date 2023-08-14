package no.nav.hjelpemidler.joark.pdf

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.Base64

class SøknadPdfGeneratorClientTest {
    private val pdf = Base64.getUrlEncoder().encode("foobar".toByteArray())
    private val client = SøknadPdfGeneratorClient(
        engine = MockEngine {
            respond(pdf, HttpStatusCode.OK)
        }
    )

    @Test
    fun `genererer pdf`() {
        runBlocking {
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
}
