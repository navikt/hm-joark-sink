package no.nav.hjelpemidler.joark.pdf

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.joark.Configuration
import org.junit.jupiter.api.Test
import java.util.Base64

class PdfClientTest {
    private val pdf = Base64.getEncoder().encode("foobar".toByteArray())
    private val pdfClient = PdfClient(
        baseUrl = Configuration.PDF_BASEURL,
        engine = MockEngine {
            respond(pdf, HttpStatusCode.OK)
        }
    )

    @Test
    fun `genererer pdf`() {
        runBlocking {
            val response = pdfClient.genererSøknadPdf(
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
