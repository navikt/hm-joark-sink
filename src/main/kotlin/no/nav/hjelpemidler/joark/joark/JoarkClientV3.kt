package no.nav.hjelpemidler.joark.joark

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
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
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.openID
import no.nav.hjelpemidler.joark.joark.model.AvsenderMottaker
import no.nav.hjelpemidler.joark.joark.model.Bruker
import no.nav.hjelpemidler.joark.joark.model.Sak

class JoarkClientV3(
    private val baseUrl: String,
    private val scope: String,
    private val azureADClient: OpenIDClient,
    engine: HttpClientEngine = CIO.create(),
) {
    private val client = createHttpClient(engine = engine) {
        expectSuccess = false
        defaultRequest {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
        }
        openID(scope, azureADClient)
    }

    suspend fun oppdaterJournalpost(oppdatertJournalpost: OppdatertJournalpost) =
        withContext(Dispatchers.IO) {
            val journalpostId = oppdatertJournalpost.journalpostId
            client
                .put("$baseUrl/journalpost/$journalpostId") {
                    setBody(oppdatertJournalpost)
                }
                .expect(HttpStatusCode.OK)
        }

    suspend fun ferdigstillJournalpost(ferdigstiltJournalpost: FerdigstiltJournalpost) =
        withContext(Dispatchers.IO) {
            val journalpostId = ferdigstiltJournalpost.journalpostId
            client
                .patch("$baseUrl/journalpost/$journalpostId/ferdigstill") {
                    setBody(ferdigstiltJournalpost)
                }
                .expect(HttpStatusCode.OK)
        }

    private suspend fun HttpResponse.expect(expected: HttpStatusCode) = when (status) {
        expected -> Unit
        else -> error("Uventet svar fra tjeneste, kall: '${request.method.value} ${request.url}', status: '${status}', body: '${bodyAsText()}'")
    }

    data class OppdatertJournalpost(
        @JsonIgnore
        val journalpostId: String,
        val bruker: Bruker,
        val tittel: String,
        val sak: Sak,
        val avsenderMottaker: AvsenderMottaker
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
