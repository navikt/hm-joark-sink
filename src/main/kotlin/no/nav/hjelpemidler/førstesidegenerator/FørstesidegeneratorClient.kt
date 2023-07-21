package no.nav.hjelpemidler.førstesidegenerator

import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import mu.KotlinLogging
import no.nav.hjelpemidler.domain.Språkkode
import no.nav.hjelpemidler.førstesidegenerator.models.Adresse
import no.nav.hjelpemidler.førstesidegenerator.models.Bruker
import no.nav.hjelpemidler.førstesidegenerator.models.PostFoerstesideRequest
import no.nav.hjelpemidler.førstesidegenerator.models.PostFoerstesideResponse
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.bearerAuth
import no.nav.hjelpemidler.joark.Configuration
import no.nav.hjelpemidler.saf.enums.Tema

private val log = KotlinLogging.logger {}

class FørstesidegeneratorClient(
    private val baseUrl: String = Configuration.FORSTESIDEGENERATOR_BASE_URL,
    private val scope: String = Configuration.FORSTESIDEGENERATOR_SCOPE,
    private val azureADClient: OpenIDClient,
    engine: HttpClientEngine = CIO.create(),
) {
    private val client = createHttpClient(engine) {
        expectSuccess = false
        defaultRequest {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            correlationId()
        }
    }

    suspend fun lagFørsteside(
        språkkode: Språkkode = Språkkode.NB,
        overskrift: String,
        fnrBruker: String,
        navSkjemaId: String,
    ): ByteArray {
        val tokenSet = azureADClient.grant(scope)
        val response = client.post("$baseUrl/test") {
            bearerAuth(tokenSet)
            setBody(
                PostFoerstesideRequest(
                    spraakkode = språkkode.førstesidegenerator,
                    overskriftstittel = overskrift,
                    foerstesidetype = PostFoerstesideRequest.Foerstesidetype.ETTERSENDELSE,
                    adresse = defaultAdresse,
                    avsender = null,
                    bruker = Bruker(fnrBruker, Bruker.BrukerType.PERSON),
                    tema = Tema.HJE.toString(),
                    navSkjemaId = navSkjemaId,
                    dokumentlisteFoersteside = vedlegg[språkkode],
                    vedleggsliste = vedlegg[språkkode],
                )
            )
        }
        return when (response.status) {
            HttpStatusCode.OK -> {
                val body = response.body<PostFoerstesideResponse>()
                log.info { "Førsteside ble generert med løpenummer: ${body.loepenummer}" }
                checkNotNull(body.foersteside) {
                    "Mangler førsteside i svaret fra førstesidegenerator!"
                }
            }

            else -> response.feilmelding()
        }
    }

    private suspend fun HttpResponse.feilmelding(): Nothing {
        val body = runCatching { bodyAsText() }.getOrElse { it.message }
        error("Uventet svar fra førstesidegenerator, kall: '${request.method.value} ${request.url}', status: '${status}', body: '$body'")
    }
}

val vedlegg = mapOf(
    Språkkode.NB to listOf("Se vedlagte brev"),
    Språkkode.NN to listOf("Sjå vedlagte brev"),
    Språkkode.EN to listOf("See the attached letters"),
)

val defaultAdresse = Adresse(
    adresselinje1 = "Nav Skanning",
    adresselinje2 = "Postboks 1400",
    postnummer = "0109",
    poststed = "OSLO",
)
