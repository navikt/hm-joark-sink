package no.nav.hjelpemidler.joark.pdf

import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import mu.KotlinLogging
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.bearerAuth
import no.nav.hjelpemidler.joark.Configuration

typealias OpprettFørstesideRequest = no.nav.hjelpemidler.joark.førstesidegenerator.models.PostFoerstesideRequest
typealias OpprettFørstesideResponse = no.nav.hjelpemidler.joark.førstesidegenerator.models.PostFoerstesideResponse
typealias FørstesideResponse = no.nav.hjelpemidler.joark.førstesidegenerator.models.FoerstesideResponse

private val log = KotlinLogging.logger {}

class FørstesidegeneratorClient(
    baseUrl: String = Configuration.FORSTESIDEGENERATOR_BASE_URL,
    private val scope: String = Configuration.FORSTESIDEGENERATOR_SCOPE,
    private val azureADClient: OpenIDClient,
    engine: HttpClientEngine = CIO.create(),
) {
    private val client = createHttpClient(engine) {
        expectSuccess = false
        defaultRequest {
            url(baseUrl)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            correlationId()
        }
    }

    suspend fun lagFørsteside(request: OpprettFørstesideRequest): Førsteside {
        val tokenSet = azureADClient.grant(scope)
        val response = client.post("foersteside") {
            bearerAuth(tokenSet)
            setBody(request)
        }
        return when (response.status) {
            HttpStatusCode.Created -> {
                val body = response.body<OpprettFørstesideResponse>()
                val løpenummer = checkNotNull(body.loepenummer) {
                    "Mangler løpenummer i svaret fra førstesidegenerator!"
                }
                val førsteside = hentFørsteside(løpenummer)
                log.info {
                    "Førsteside ble generert med løpenummer: $løpenummer, arkivtittel: '${førsteside.arkivtittel}', navSkjemaId: '${førsteside.navSkjemaId}', saksnummer: ${førsteside.arkivsak?.arkivsaksnummer}"
                }
                val fysiskDokument = checkNotNull(body.foersteside) {
                    "Mangler førsteside i svaret fra førstesidegenerator!"
                }
                Førsteside(fysiskDokument, request.overskriftstittel, request.navSkjemaId)
            }

            else -> response.feilmelding()
        }
    }

    suspend fun hentFørsteside(løpenummer: String): FørstesideResponse {
        log.info { "Henter førsteside med løpenummer: $løpenummer" }
        val tokenSet = azureADClient.grant(scope)
        val response = client.get("foersteside/$løpenummer") {
            bearerAuth(tokenSet)
        }
        return when (response.status) {
            HttpStatusCode.OK -> response.body<FørstesideResponse>()
            else -> response.feilmelding()
        }
    }

    private suspend fun HttpResponse.feilmelding(): Nothing {
        val body = runCatching { bodyAsText() }.getOrElse { it.message }
        error("Uventet svar fra førstesidegenerator, kall: '${request.method.value} ${request.url}', status: '${status}', body: '$body'")
    }
}
