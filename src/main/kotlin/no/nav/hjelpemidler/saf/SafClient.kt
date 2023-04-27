package no.nav.hjelpemidler.saf

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import mu.KotlinLogging
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.bearerAuth
import no.nav.hjelpemidler.joark.Configuration
import no.nav.hjelpemidler.saf.enums.Variantformat
import no.nav.hjelpemidler.saf.hentjournalpost.Journalpost
import java.net.URL
import java.util.UUID

private val log = KotlinLogging.logger {}

class SafClient(
    baseUrlGraphQL: String = Configuration.SAF_GRAPHQL_URL,
    private val baseUrlRest: String = Configuration.SAF_REST_URL,
    private val scope: String = Configuration.SAF_SCOPE,
    private val azureADClient: OpenIDClient,
    engine: HttpClientEngine = CIO.create(),
) {
    private val clientGraphQL = GraphQLKtorClient(
        url = URL(baseUrlGraphQL),
        httpClient = HttpClient(engine) {
            expectSuccess = true
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(maxRetries = 5)
                exponentialDelay()
            }
            defaultRequest {
                header("X-Correlation-ID", UUID.randomUUID().toString())
            }
        })
    private val clientRest = createHttpClient(engine) {
        expectSuccess = false
        defaultRequest {
            val id = UUID.randomUUID().toString()
            header("X-Correlation-ID", id)
            header("Nav-CallId", id)
        }
    }

    suspend fun hentJournalpost(journalpostId: String): Journalpost? {
        val tokenSet = azureADClient.grant(scope)
        val response =
            clientGraphQL.execute(HentJournalpost(HentJournalpost.Variables(journalpostId = journalpostId))) {
                bearerAuth(tokenSet)
            }
        val result = response.resultOrThrow()
        return result.journalpost
    }

    suspend fun hentDokument(journalpostId: String, dokumentInfoId: String, variantformat: Variantformat): String {
        val url = "$baseUrlRest/hentdokument/$journalpostId/$dokumentInfoId/$variantformat"
        log.info {
            "Henter dokument fra SAF, url: '$url'"
        }
        val tokenSet = azureADClient.grant(scope)
        val response = clientRest.get(url) {
            accept(ContentType.Any)
            bearerAuth(tokenSet)
        }
        return when (response.status) {
            HttpStatusCode.OK -> response.bodyAsText()
            else -> {
                val body = runCatching { response.bodyAsText() }.getOrElse { it.message }
                error("Uventet svar fra SAF, status: '${response.status}', body: '$body'")
            }
        }
    }
}
