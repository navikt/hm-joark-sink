package no.nav.hjelpemidler.joark.brev

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.joark.Configuration

private val log = KotlinLogging.logger {}

class BrevClient(
    private val baseUrl: String = Configuration.BREV_API_URL,
    engine: HttpClientEngine = CIO.create(),
) {
    private val client = createHttpClient(engine = engine) {
        expectSuccess = true
        defaultRequest {
            accept(ContentType.Application.Pdf)
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun lagBrev(request: HentBrevRequest<*>): HttpResponse {
        val url = "$baseUrl/${request.brevmal.apiNavn}/${request.målform.urlNavn}/pdf"
        log.info { "Henter brev med url: '$url'" }
        val response: HttpResponse = client.post(url) {
            setBody(request.data)
        }
        return response
    }
}

data class HentBrevRequest<out T : Any>(
    val brevmal: Brevmal,
    val målform: Målform,
    val data: T,
)
