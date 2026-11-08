package no.nav.hjelpemidler.joark.sink.pdf

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.entraId
import no.nav.hjelpemidler.joark.sink.Configuration
import java.io.Closeable
import java.util.UUID

private val log = KotlinLogging.logger {}

class SøknadApiClient(
    engine: HttpClientEngine,
    baseUrl: String = Configuration.SOKNAD_API_URL,
) : Closeable {
    private val client = createHttpClient(engine) {
        expectSuccess = false
        entraId(Configuration.SOKNAD_API_SCOPE)
        defaultRequest {
            url(baseUrl)
            accept(ContentType.Application.Pdf)
        }
    }

    private val baseUrl = "/hm/hm-joark-sink/pdf"

    suspend fun hentBehovsmeldingPdf(id: UUID): ByteArray {
        log.info { "Henter PDF fra hm-soknad-api for id: $id" }
        return hentPdf("$baseUrl/$id")
    }

    suspend fun hentVedleggPdf(behovsmeldingId: UUID, vedleggId: UUID): ByteArray {
        log.info { "Henter vedlegg-PDF fra hm-soknad-api for vedlegg $vedleggId på behovsmelding $behovsmeldingId" }
        return hentPdf("$baseUrl/$behovsmeldingId/vedlegg/$vedleggId")
    }

    private suspend fun hentPdf(url: String): ByteArray {
        val response = client.get(url)
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            else -> {
                val body = runCatching { response.bodyAsText() }.getOrElse { it.message }
                throw PdfClientException("Uventet status: '${response.status}', body: '$body'")
            }
        }
    }

    override fun close() {
        client.close()
    }
}
