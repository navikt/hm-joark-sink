package no.nav.hjelpemidler.joark.pdf

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.joark.Configuration
import org.intellij.lang.annotations.Language

private val log = KotlinLogging.logger {}

class SøknadPdfGeneratorClient(
    engine: HttpClientEngine = CIO.create(),
    baseUrl: String = Configuration.SOKNAD_PDFGEN_BASE_URL,
) {
    private val client = createHttpClient(engine) {
        expectSuccess = false
        defaultRequest {
            url(baseUrl)
            accept(ContentType.Application.Pdf)
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun genererPdfBarnebriller(@Language("JSON") søknadJson: String): ByteArray =
        genererPdf(søknadJson, "barnebrille/barnebrille")

    private suspend fun genererPdf(@Language("JSON") søknadJson: String, path: String): ByteArray {
        log.info { "Genererer PDF for path: '$path'" }
        val response = client.post(path) { setBody(søknadJson) }
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            else -> {
                val body = runCatching { response.bodyAsText() }.getOrElse { it.message }
                throw PdfClientException("Uventet status: '${response.status}', body: '$body'")
            }
        }
    }
}
