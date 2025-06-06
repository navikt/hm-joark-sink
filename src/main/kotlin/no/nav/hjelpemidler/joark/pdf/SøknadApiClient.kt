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

    suspend fun hentPdf(id: UUID): ByteArray {
        log.info { "Henter PDF fra hm-soknad-api for id: $id" }
        val response = client.get("/hm/hm-joark-sink/pdf/$id")
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            else -> {
                val body = runCatching { response.bodyAsText() }.getOrElse { it.message }
                throw PdfClientException("Uventet status: '${response.status}', body: '$body'")
            }
        }
    }
}
