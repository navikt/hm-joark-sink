package no.nav.hjelpemidler.saf

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.defaultRequest
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.bearerAuth
import no.nav.hjelpemidler.joark.Configuration
import no.nav.hjelpemidler.saf.hentjournalpost.Journalpost
import java.net.URI

private val log = KotlinLogging.logger {}

class SafClient(
    baseUrlGraphQL: String = Configuration.SAF_GRAPHQL_URL,
    private val scope: String = Configuration.SAF_SCOPE,
    private val azureADClient: OpenIDClient,
    engine: HttpClientEngine = CIO.create(),
) {
    private val clientGraphQL = GraphQLKtorClient(
        url = URI(baseUrlGraphQL).toURL(),
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
}
