package no.nav.hjelpemidler.joark.pdf

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.joark.Configuration
import no.nav.hjelpemidler.joark.brev.Målform

private val log = KotlinLogging.logger {}

class PdfGeneratorClient(
    engine: HttpClientEngine = CIO.create(),
    baseUrl: String = Configuration.PDF_GENERATOR_BASE_URL,
) {
    private val client = createHttpClient(engine) {
        expectSuccess = false
        defaultRequest {
            url(baseUrl)
            accept(ContentType.Application.Json)
        }
    }

    suspend fun kombinerPdf(vararg pdf: ByteArray): ByteArray {
        log.info { "Kombinerer ${pdf.size} dokumenter" }
        val response = client.submitFormWithBinaryData("kombiner-til-pdf", formData {
            pdf.forEachIndexed { index, bytes ->
                val name = "pdf_$index"
                append(name, bytes, Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Pdf.toString())
                    append(HttpHeaders.ContentDisposition, "filename=\"$name.pdf\"")
                })
            }
        })
        return when (response.status) {
            HttpStatusCode.OK -> response.body<ByteArray>()
            else -> {
                val body = runCatching { response.bodyAsText() }.getOrElse { it.message }
                throw PdfClientException("Uventet status: '${response.status}', body: '$body'")
            }
        }
    }

    suspend fun lagBrev(mappe: String, brevId: String, målform: Målform, data: Any): ByteArray {
        log.info { "Generer pdf med målform=$målform" }
        val response = client.post("brev/$mappe/$brevId/$målform") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(data)
        }
        return when (response.status) {
            HttpStatusCode.OK -> response.body<ByteArray>()
            else -> {
                val body = runCatching { response.bodyAsText() }.getOrElse { it.message }
                throw PdfClientException("Uventet status: '${response.status}', body: '$body'")
            }
        }
    }
}
