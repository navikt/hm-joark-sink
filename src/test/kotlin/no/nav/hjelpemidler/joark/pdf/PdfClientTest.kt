package no.nav.hjelpemidler.joark.pdf

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.joark.pdf.PdfClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64

internal class PdfClientTest {

    companion object {
        val server: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun start() {
            server.start()
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            server.stop()
        }
    }

    private val pdfClient: PdfClient

    init {
        pdfClient = PdfClient(server.baseUrl())
    }

    @BeforeEach
    fun configure() {
        WireMock.configureFor(server.port())
    }

    @Test
    fun `genererer pdf`() {

        runBlocking {
            val mockByteArray = pdfResponse.toByteArray()
            val stringPdfMock = Base64.getEncoder().encodeToString(mockByteArray)

            WireMock.stubFor(WireMock.post("/api/v1/genpdf/hmb/hmb").willReturn(WireMock.ok(stringPdfMock)))

            pdfClient.genererPdf(pdfResponse)

            WireMock.verify(1, postRequestedFor(urlEqualTo("/api/v1/genpdf/hmb/hmb")))
        }
    }

    private val pdfResponse =
        """
{
    "soknad": "soknad"
}
                """
}
