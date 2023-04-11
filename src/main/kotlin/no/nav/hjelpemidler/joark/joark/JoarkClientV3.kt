package no.nav.hjelpemidler.joark.joark

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.*
import io.ktor.client.request.accept
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.openID
import no.nav.hjelpemidler.joark.joark.model.AvsenderMottaker
import no.nav.hjelpemidler.joark.joark.model.Bruker
import no.nav.hjelpemidler.joark.joark.model.Sak

private val logger = KotlinLogging.logger {}

class JoarkClientV3(
    private val baseUrl: String,
    private val scope: String,
    private val azureADClient: OpenIDClient,
    engine: HttpClientEngine = CIO.create(),
) {
    private val client = createHttpClient(engine) {
        expectSuccess = false
        defaultRequest {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
        }
        install(HttpRequestRetry) {
            retryOnExceptionOrServerErrors(maxRetries = 5)
            exponentialDelay()
        }
        openID(scope, azureADClient)
    }

    suspend fun oppdaterJournalpost(oppdatertJournalpost: OppdatertJournalpost) =
        withContext(Dispatchers.IO) {
            val journalpostId = oppdatertJournalpost.journalpostId
            val url = "$baseUrl/journalpost/$journalpostId"
            logger.info {
                "Oppdaterer journalpost med url: '$url'"
            }
            client
                .put(url) {
                    setBody(oppdatertJournalpost)
                }
                .expect(HttpStatusCode.OK)
        }

    suspend fun ferdigstillJournalpost(ferdigstiltJournalpost: FerdigstiltJournalpost) =
        withContext(Dispatchers.IO) {
            val journalpostId = ferdigstiltJournalpost.journalpostId
            val url = "$baseUrl/journalpost/$journalpostId/ferdigstill"
            logger.info {
                "Ferdigstiller journalpost med url: '$url'"
            }
            client
                .patch(url) {
                    setBody(ferdigstiltJournalpost)
                }
                .expect(HttpStatusCode.OK)
        }

    private suspend fun HttpResponse.expect(expected: HttpStatusCode) = when (status) {
        expected -> Unit
        else -> {
            val body = runCatching { bodyAsText() }.getOrElse { it.message }
            error("Uventet svar fra tjeneste, kall: '${request.method.value} ${request.url}', status: '${status}', body: '$body'")
        }
    }

    data class OppdatertJournalpost(
        @JsonIgnore
        val journalpostId: String,
        val bruker: Bruker,
        val tittel: String,
        val sak: Sak,
        val avsenderMottaker: AvsenderMottaker,
    ) {
        val tema = "HJE"
    }

    data class FerdigstiltJournalpost(
        @JsonIgnore
        val journalpostId: String,
        @JsonProperty("journalfoerendeEnhet")
        val journalførendeEnhet: String,
    )
}
