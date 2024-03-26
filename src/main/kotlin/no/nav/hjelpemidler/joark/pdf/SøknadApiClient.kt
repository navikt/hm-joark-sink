package no.nav.hjelpemidler.joark.pdf

import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import mu.KotlinLogging
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.bearerAuth
import no.nav.hjelpemidler.joark.Configuration
import java.util.UUID

private val log = KotlinLogging.logger {}

class SøknadApiClient(
    baseUrl: String = Configuration.SOKNAD_API_URL,
    private val scope: String = Configuration.SOKNAD_API_SCOPE,
    private val azureADClient: OpenIDClient,
    engine: HttpClientEngine = CIO.create(),
) {
    private val client = createHttpClient(engine) {
        expectSuccess = false
        defaultRequest {
            url(baseUrl)
            accept(ContentType.Application.Pdf)
        }
    }

    suspend fun hentPdf(id: UUID): ByteArray {
        log.info { "Henter PDF fra hm-soknad-api for $id" }
        val tokenSet = azureADClient.grant(scope)
        val response = client.get("/pdf/$id") {
            bearerAuth(tokenSet)
        }
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            else -> {
                val body = runCatching { response.bodyAsText() }.getOrElse { it.message }
                throw PdfClientException("Uventet status: '${response.status}', body: '$body'")
            }
        }
    }
}