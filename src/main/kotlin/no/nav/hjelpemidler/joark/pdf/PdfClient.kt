package no.nav.hjelpemidler.joark.pdf

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

internal class PdfClient(private val baseUrl: String) {
    private val API_BASE_PATH = "api/v1/genpdf"

    private val client = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) {
            jackson()
        }
    }

    suspend fun genererSoknadPdf(soknadJson: String): ByteArray = genererPdf(soknadJson, "$API_BASE_PATH/hmb/hmb")

    suspend fun genererBarnebrillePdf(soknadJson: String): ByteArray =
        genererPdf(soknadJson, "$API_BASE_PATH/barnebrille/barnebrille")

    suspend fun genererPdf(soknadJson: String, path: String): ByteArray {
        logger.info { "Generer PDF for path: $path" }
        val response = client.post("$baseUrl/$path") {
            contentType(ContentType.Application.Json)
            setBody(soknadJson)
        }
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            else -> throw PdfException("Uventet status: ${response.status}, body: '${response.bodyAsText()}'")
        }
    }
}

internal class PdfException(msg: String) : RuntimeException(msg)
