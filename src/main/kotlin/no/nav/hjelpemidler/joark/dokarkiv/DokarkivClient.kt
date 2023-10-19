package no.nav.hjelpemidler.joark.dokarkiv

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
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
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.logging
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.openID
import no.nav.hjelpemidler.joark.Configuration
import no.nav.hjelpemidler.joark.dokarkiv.models.AvsenderMottaker
import no.nav.hjelpemidler.joark.dokarkiv.models.Bruker
import no.nav.hjelpemidler.joark.dokarkiv.models.FerdigstillJournalpostRequest
import no.nav.hjelpemidler.joark.dokarkiv.models.KnyttTilAnnenSakRequest
import no.nav.hjelpemidler.joark.dokarkiv.models.KnyttTilAnnenSakResponse
import no.nav.hjelpemidler.joark.dokarkiv.models.OppdaterJournalpostRequest
import no.nav.hjelpemidler.joark.dokarkiv.models.OppdaterJournalpostResponse
import no.nav.hjelpemidler.joark.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.joark.dokarkiv.models.OpprettJournalpostResponse
import no.nav.hjelpemidler.joark.dokarkiv.models.Sak
import no.nav.hjelpemidler.joark.ktor.navUserId

private val log = KotlinLogging.logger {}
private val secureLog = KotlinLogging.logger("tjenestekall")

class DokarkivClient(
    baseUrl: String = Configuration.JOARK_BASE_URL,
    private val scope: String = Configuration.JOARK_SCOPE,
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
            url(baseUrl)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            correlationId()
        }
        openID(scope, azureADClient)
        logging {
            logger = object : Logger {
                override fun log(message: String) {
                    secureLog.info(message)
                }
            }
            level = LogLevel.HEADERS
        }
    }

    suspend fun opprettJournalpost(
        opprettJournalpostRequest: OpprettJournalpostRequest,
        forsøkFerdigstill: Boolean = false,
        opprettetAv: String? = null,
    ): OpprettJournalpostResponse {
        val url = "journalpost"
        val eksternReferanseId = opprettJournalpostRequest.eksternReferanseId
        log.info {
            "Oppretter journalpost med url: '$url', eksternReferanseId: $eksternReferanseId"
        }
        val response = client.post(url) {
            parameter("forsoekFerdigstill", forsøkFerdigstill)
            navUserId(opprettetAv)
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
        val url = "journalpost/$journalpostId"
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
        val url = "journalpost/$journalpostId/ferdigstill"
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
        val url = "journalpost/$journalpostId/feilregistrer/feilregistrerSakstilknytning"
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

    suspend fun knyttTilAnnenSak(
        journalpostId: String,
        knyttTilAnnenSakRequest: KnyttTilAnnenSakRequest,
    ): KnyttTilAnnenSakResponse {
        val url = "journalpost/$journalpostId/knyttTilAnnenSak"
        log.info {
            "Knytter til annen sak med url: '$url', journalpostId: $journalpostId"
        }
        val response = client.put(url) {
            setBody(knyttTilAnnenSakRequest)
        }
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
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

fun avsenderMottakerMedFnr(fnr: String): AvsenderMottaker =
    AvsenderMottaker(fnr, AvsenderMottaker.IdType.FNR, null)

fun brukerMedFnr(fnr: String): Bruker =
    Bruker(fnr, Bruker.IdType.FNR)

fun fagsakHjelpemidler(sakId: String): Sak =
    Sak(
        fagsakId = sakId,
        fagsaksystem = Sak.Fagsaksystem.HJELPEMIDLER,
        sakstype = Sak.Sakstype.FAGSAK,
    )

fun fagsakBarnebriller(sakId: String): Sak =
    Sak(
        fagsakId = sakId,
        fagsaksystem = Sak.Fagsaksystem.BARNEBRILLER,
        sakstype = Sak.Sakstype.FAGSAK,
    )

fun generellSak(): Sak =
    Sak(
        sakstype = Sak.Sakstype.GENERELL_SAK,
    )
