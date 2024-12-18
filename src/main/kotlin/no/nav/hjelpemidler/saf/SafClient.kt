package no.nav.hjelpemidler.saf

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.defaultRequest
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.http.openid.TokenSetProvider
import no.nav.hjelpemidler.http.openid.openID
import no.nav.hjelpemidler.joark.Configuration
import no.nav.hjelpemidler.saf.hentjournalpost.Journalpost
import java.net.URI

private val log = KotlinLogging.logger {}

class SafClient(
    tokenSetProvider: TokenSetProvider,
    engine: HttpClientEngine = CIO.create(),
    baseUrlGraphQL: String = Configuration.SAF_GRAPHQL_URL,
) {
    private val clientGraphQL = GraphQLKtorClient(
        url = URI(baseUrlGraphQL).toURL(),
        httpClient = HttpClient(engine) {
            expectSuccess = true
            openID(tokenSetProvider)
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(maxRetries = 5)
                exponentialDelay()
            }
            defaultRequest {
                correlationId()
            }
        })

    suspend fun hentJournalpost(journalpostId: String): Journalpost? {
        val request = HentJournalpost(HentJournalpost.Variables(journalpostId = journalpostId))
        val response = clientGraphQL.execute(request)
        val result = response.resultOrThrow()
        return result.journalpost
    }

    suspend fun hentJournalposterForSak(sakId: String): List<no.nav.hjelpemidler.saf.hentdokumentoversiktsak.Journalpost> {
        val request = HentDokumentoversiktSak(HentDokumentoversiktSak.Variables(fagsakId = sakId))
        val response = clientGraphQL.execute(request)
        val result = response.resultOrThrow()
        return result.dokumentoversiktFagsak.journalposter.filterNotNull()
    }
}
