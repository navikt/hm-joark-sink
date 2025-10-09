package no.nav.hjelpemidler.joark.pdf

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.TokenSetProvider
import no.nav.hjelpemidler.http.openid.openID
import no.nav.hjelpemidler.joark.Configuration
import java.util.UUID

private val log = KotlinLogging.logger {}

class SøknadApiClient(
    tokenSetProvider: TokenSetProvider,
    engine: HttpClientEngine = CIO.create(),
    baseUrl: String = Configuration.SOKNAD_API_URL,
) {
    private val client = createHttpClient(engine) {
        expectSuccess = false
        openID(tokenSetProvider)
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

    suspend private fun hentPdf(url: String): ByteArray {
        val response = client.get(url)
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            else -> {
                val body = runCatching { response.bodyAsText() }.getOrElse { it.message }
                throw PdfClientException("Uventet status: '${response.status}', body: '$body'")
            }
        }
    }
}
