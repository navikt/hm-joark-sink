package no.nav.hjelpemidler.joark.pdf

import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import mu.KotlinLogging
import no.nav.hjelpemidler.http.createHttpClient
import org.intellij.lang.annotations.Language

private val logger = KotlinLogging.logger {}

class PdfClient(
    private val baseUrl: String,
    engine: HttpClientEngine = CIO.create(),
) {
    private val basePath = "api/v1/genpdf"

    private val client = createHttpClient(engine) {
        expectSuccess = false
    }

    suspend fun genererSøknadPdf(@Language("JSON") søknadJson: String): ByteArray =
        genererPdf(søknadJson, "$basePath/hmb/hmb")

    suspend fun genererBarnebrillePdf(@Language("JSON") søknadJson: String): ByteArray =
        genererPdf(søknadJson, "$basePath/barnebrille/barnebrille")

    private suspend fun genererPdf(@Language("JSON") søknadJson: String, path: String): ByteArray {
        logger.info { "Generer PDF for path: $path" }
        val response = client.post("$baseUrl/$path") {
            contentType(ContentType.Application.Json)
            setBody(søknadJson)
        }
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            else -> throw PdfException("Uventet status: ${response.status}, body: '${response.bodyAsText()}'")
        }
    }
}

class PdfException(message: String) : RuntimeException(message)
