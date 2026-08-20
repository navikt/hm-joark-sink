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

private val log = KotlinLogging.logger {}

class DelbestillingPdfClient(
    engine: HttpClientEngine = CIO.create(),
    tokenSetProvider: TokenSetProvider,
    baseUrl: String = Configuration.DELBESTILLING_API_URL,
) {
    private val client = createHttpClient(engine) {
        expectSuccess = false
        openID(tokenSetProvider)
        defaultRequest {
            url(baseUrl)
            accept(ContentType.Application.Pdf)
        }
    }

    private val pdf_endepunkt = "delbestilling/pdf"

    suspend fun hentDelbestillingPdf(saksnr: Long) : ByteArray {
        log.info { "Henter PDF fra hm-delbestilling-api for saksnr: $saksnr" }
        return hentPdf("$pdf_endepunkt/$saksnr")
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