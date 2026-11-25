package no.nav.hjelpemidler.joark.pdf

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.joark.sink.pdf.PdfClientException
import no.nav.hjelpemidler.joark.sink.pdf.PdfGeneratorClient
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class PdfGeneratorClientTest {
    @Test
    fun `barnebrille sender uendret JSON til ny generator`() = runTest {
        val json = """{"beløp":1200.75,"brilleseddel":{},"opprettet":"2026-11-24T10:15:30"}"""
        val pdf = "%PDF-test".toByteArray()
        PdfGeneratorClient(
            engine = MockEngine { request ->
                request.method shouldBe HttpMethod.Post
                request.url.toString() shouldBe "http://pdf-generator/api/barnebrille"
                request.body.contentType shouldBe ContentType.Application.Json
                request.headers[HttpHeaders.Accept] shouldBe ContentType.Application.Pdf.toString()
                request.body.shouldBeInstanceOf<TextContent>().text shouldBe json
                respond(pdf, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Pdf.toString()))
            },
            baseUrl = "http://pdf-generator/api/",
        ).use { client ->
            assertContentEquals(pdf, client.genererPdfBarnebriller(json))
        }
    }

    @Test
    fun `barnebrille videreformidler feil fra generatoren`() = runTest {
        PdfGeneratorClient(
            engine = MockEngine { respond("Feil under generering", HttpStatusCode.InternalServerError) },
            baseUrl = "http://pdf-generator/api/",
        ).use { client ->
            assertFailsWith<PdfClientException> { client.genererPdfBarnebriller("{}") }
        }
    }

    @Test
    fun `kombinerer pdf`() = runTest {
        val client = PdfGeneratorClient(engine = MockEngine { request ->
            request.url.fullPath shouldBe "/api/kombiner-til-pdf"
            respond(ByteArray(0))
        })
        client.kombinerPdf(ByteArray(0))
    }
}
