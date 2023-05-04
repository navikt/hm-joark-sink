package no.nav.hjelpemidler.dokarkiv

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import mu.KotlinLogging
import no.nav.hjelpemidler.dokarkiv.models.AvsenderMottaker
import no.nav.hjelpemidler.dokarkiv.models.Bruker
import no.nav.hjelpemidler.dokarkiv.models.FerdigstillJournalpostRequest
import no.nav.hjelpemidler.dokarkiv.models.OppdaterJournalpostRequest
import no.nav.hjelpemidler.dokarkiv.models.OppdaterJournalpostResponse
import no.nav.hjelpemidler.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.dokarkiv.models.OpprettJournalpostResponse
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.openID

private val log = KotlinLogging.logger {}

class DokarkivClient(
    private val baseUrl: String,
    private val scope: String,
    private val azureADClient: OpenIDClient,
    engine: HttpClientEngine = CIO.create(),
) {
    private val client = createHttpClient(engine) {
        expectSuccess = false
        install(HttpRequestRetry) {
            maxRetries = 5
            retryOnExceptionOrServerErrors()
            exponentialDelay()
        }
        defaultRequest {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
        }
        openID(scope, azureADClient)
    }

    suspend fun opprettJournalpost(
        opprettJournalpostRequest: OpprettJournalpostRequest,
        forsøkFerdigstill: Boolean = false,
    ): OpprettJournalpostResponse {
        val url = "$baseUrl/journalpost"
        val eksternReferanseId = opprettJournalpostRequest.eksternReferanseId
        log.info {
            "Oppretter journalpost med url: '$url', eksternReferanseId: $eksternReferanseId"
        }
        val response = client.post(url) {
            parameter("forsoekFerdigstill", forsøkFerdigstill)
            setBody(opprettJournalpostRequest)
        }
        val journalpost: OpprettJournalpostResponse = when (response.status) {
            HttpStatusCode.Created -> response.body()
            HttpStatusCode.Conflict -> {
                // skjer ved opprettelse av ny journalpost med samme eksternReferanseId
                log.warn { "Duplikatvarsel ved opprettelse av journalpost, eksternReferanseId: $eksternReferanseId" }
                response.body()
            }

            else -> response.feilmelding()
        }
        if (forsøkFerdigstill != journalpost.journalpostferdigstilt) {
            dokarkivError("Journalpost ble ikke ferdigstilt, journalpostId: ${journalpost.journalpostId}")
        }
        return journalpost
    }

    suspend fun oppdaterJournalpost(
        journalpostId: String,
        oppdaterJournalpostRequest: OppdaterJournalpostRequest,
    ): OppdaterJournalpostResponse {
        val url = "$baseUrl/journalpost/$journalpostId"
        log.info {
            "Oppdaterer journalpost med url: '$url', journalpostId: $journalpostId"
        }
        val response = client.put(url) {
            setBody(oppdaterJournalpostRequest)
        }
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            else -> response.feilmelding()
        }
    }

    suspend fun ferdigstillJournalpost(
        journalpostId: String,
        ferdigstillJournalpostRequest: FerdigstillJournalpostRequest,
    ) {
        val url = "$baseUrl/journalpost/$journalpostId/ferdigstill"
        log.info {
            "Ferdigstiller journalpost med url: '$url', journalpostId: $journalpostId"
        }
        val response = client.patch(url) {
            setBody(ferdigstillJournalpostRequest)
        }
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            else -> response.feilmelding()
        }
    }

    suspend fun feilregistrerSakstilknytning(journalpostId: String) {
        val url = "$baseUrl/journalpost/$journalpostId/feilregistrer/feilregistrerSakstilknytning"
        log.info {
            "Feilregistrerer sakstilknytning med url: '$url', journalpostId: $journalpostId"
        }
        val response = client.patch(url)
        return when (response.status) {
            HttpStatusCode.OK -> Unit
            HttpStatusCode.BadRequest -> {
                val body = response.body<JsonNode>()
                when {
                    body.at("/message").textValue() == "Saksrelasjonen er allerede feilregistrert" -> {
                        log.info { "Forsøkte å feilregistrere en journalpost som allerede er feilregistrert, journalpostId: $journalpostId" }
                        return
                    }

                    else -> response.feilmelding()
                }
            }

            HttpStatusCode.Conflict -> log.info {
                "Forsøkte å feilregistrere en journalpost som allerede er feilregistrert, journalpostId: $journalpostId"
            }

            else -> response.feilmelding()
        }
    }

    private suspend fun HttpResponse.feilmelding(): Nothing {
        val body = runCatching { bodyAsText() }.getOrElse { it.message }
        dokarkivError("Uventet svar fra dokarkiv, kall: '${request.method.value} ${request.url}', status: '${status}', body: '$body'")
    }
}

class DokarkivException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)

fun dokarkivError(message: String?, cause: Throwable? = null): Nothing =
    throw DokarkivException(message, cause)

fun avsenderMottakerMedFnr(fnr: String, navn: String? = null): AvsenderMottaker =
    AvsenderMottaker(fnr, AvsenderMottaker.IdType.FNR, navn)

fun brukerMedFnr(fnr: String): Bruker =
    Bruker(fnr, Bruker.IdType.FNR)