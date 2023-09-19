package no.nav.hjelpemidler.saf

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import mu.KotlinLogging
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.bearerAuth
import no.nav.hjelpemidler.joark.Configuration
import no.nav.hjelpemidler.saf.enums.Variantformat
import no.nav.hjelpemidler.saf.hentjournalpost.Journalpost
import java.net.URL

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
                correlationId()
            }
        })

    private val clientRest = createHttpClient(engine) {
        expectSuccess = false
        defaultRequest {
            url(baseUrlRest)
            correlationId()
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

    suspend fun hentJournalposterForSak(sakId: String): List<no.nav.hjelpemidler.saf.hentdokumentoversiktsak.Journalpost> {
        val tokenSet = azureADClient.grant(scope)
        val response =
            clientGraphQL.execute(HentDokumentoversiktSak(HentDokumentoversiktSak.Variables(fagsakId = sakId))) {
                bearerAuth(tokenSet)
            }
        val result = response.resultOrThrow()
        return result.dokumentoversiktFagsak.journalposter.filterNotNull()
    }

    suspend fun hentDokument(journalpostId: String, dokumentInfoId: String, variantformat: Variantformat): ByteArray {
        val url = "hentdokument/$journalpostId/$dokumentInfoId/$variantformat"
        log.info {
            "Henter dokument fra SAF, url: '$url'"
        }
        val tokenSet = azureADClient.grant(scope)
        val response = clientRest.get(url) {
            accept(ContentType.Any)
            bearerAuth(tokenSet)
        }
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            else -> {
                val body = runCatching { response.bodyAsText() }.getOrElse { it.message }
                error("Uventet svar fra SAF, status: '${response.status}', body: '$body'")
            }
        }
    }
}
