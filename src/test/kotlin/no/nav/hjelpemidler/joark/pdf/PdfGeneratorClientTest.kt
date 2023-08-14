package no.nav.hjelpemidler.joark.pdf

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.fullPath
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PdfGeneratorClientTest {
    @Test
    fun `kombinerer pdf`() = runTest {
        val client = PdfGeneratorClient(engine = MockEngine { request ->
            request.url.fullPath shouldBe "/api/kombiner-til-pdf"
            respond(ByteArray(0))
        })
        client.kombinerPdf(ByteArray(0))
    }
}
