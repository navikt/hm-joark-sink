package no.nav.hjelpemidler.saf

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.bearerAuth
import no.nav.hjelpemidler.saf.enums.Variantformat
import no.nav.hjelpemidler.saf.hentjournalpost.Journalpost
import java.net.URL
import java.util.UUID

class SafClient(
    url: String,
    private val scope: String,
    private val azureAdClient: OpenIDClient,
    engine: HttpClientEngine = CIO.create(),
) {
    private val client = GraphQLKtorClient(
        url = URL(url),
        httpClient = HttpClient(engine = engine) {
            expectSuccess = true
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(maxRetries = 5)
                exponentialDelay()
            }
            defaultRequest {
                header("X-Correlation-ID", UUID.randomUUID().toString())
            }
        })

    suspend fun hentJournalpost(journalpostId: String): Journalpost? {
        val tokenSet = azureAdClient.grant(scope)
        val response = client.execute(HentJournalpost(HentJournalpost.Variables(journalpostId = journalpostId))) {
            bearerAuth(tokenSet)
        }
        val result = response.resultOrThrow()
        return result.journalpost
    }

    suspend fun hentDokument(journalpostId: String, dokumentInfoId: String, variantformat: Variantformat): ByteArray {
        // TODO
        return ByteArray(0)
    }
}
