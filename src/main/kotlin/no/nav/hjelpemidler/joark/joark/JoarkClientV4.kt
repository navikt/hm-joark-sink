package no.nav.hjelpemidler.joark.joark

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.patch
import io.ktor.client.request.post
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
import no.nav.hjelpemidler.joark.joark.model.Dokumenter
import no.nav.hjelpemidler.joark.joark.model.Dokumentvarianter
import no.nav.hjelpemidler.joark.joark.model.OpprettOgFerdigstillJournalpostRequest
import no.nav.hjelpemidler.joark.joark.model.Sak
import no.nav.hjelpemidler.saf.enums.Tema
import java.time.LocalDateTime
import java.util.Base64

private val logger = KotlinLogging.logger {}

class JoarkClientV4(
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
        openID(scope, azureADClient)
    }

    companion object {
        const val ID_TYPE = "FNR"
        const val LAND = "NORGE"
        const val DOKUMENT_KATEGORI_VED = "VED"
        const val FIL_TYPE = "PDFA"
        const val VARIANT_FORMAT = "ARKIV"
        const val TEMA = "HJE"
        const val KANAL = "NAV_NO"
        const val JOURNALPOST_TYPE = "UTGAAENDE"
        const val JOURNALPOSTBESKRIVELSE_BARNEBRILLE = "Vedtak for barnebrille"
        const val OPPRETT_OG_FERDIGSTILL_URL_PATH = "/journalpost?forsoekFerdigstill=true"
    }

    private val opprettOfFerdigstillUrl = "$baseUrl$OPPRETT_OG_FERDIGSTILL_URL_PATH"

    suspend fun opprettOgFerdigstillBarnebrillevedtak(
        fnr: String,
        pdf: ByteArray,
        sakId: String,
        dokumentTittel: String,
        navnAvsender: String,
    ): OpprettetJournalpostResponse {
        logger.info { "opprett og ferdigstill journalføring for manuelt barnebrillevedtak $dokumentTittel" }

        val requestBody = OpprettOgFerdigstillJournalpostRequest(
            AvsenderMottaker(fnr, ID_TYPE, LAND, navnAvsender),
            Bruker(fnr, ID_TYPE),
            listOf(
                Dokumenter(
                    brevkode = "vedtaksbrev_barnebriller",
                    dokumentvarianter = listOf(
                        Dokumentvarianter(
                            "barnebrille_vedtak.pdf",
                            FIL_TYPE,
                            VARIANT_FORMAT,
                            Base64.getEncoder().encodeToString(pdf)
                        )
                    ),
                    tittel = dokumentTittel
                )
            ),
            TEMA,
            JOURNALPOSTBESKRIVELSE_BARNEBRILLE,
            KANAL,
            sakId + "BARNEBRILLEVEDTAK",
            JOURNALPOST_TYPE,
            "9999",
            Sak(
                fagsakId = sakId,
                fagsaksystem = "BARNEBRILLER",
                sakstype = "FAGSAK"
            )
        )

        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                val response: HttpResponse = client.post(opprettOfFerdigstillUrl) {
                    setBody(requestBody)
                }

                when (response.status) {
                    HttpStatusCode.Created, HttpStatusCode.Conflict -> {
                        if (response.status == HttpStatusCode.Conflict) {
                            logger.warn { "Duplikatvarsel ved opprettelse av jp med sakId ${requestBody.sak.fagsakId}" }
                        }
                        val responseBody = response.body<JsonNode>()
                        if (responseBody.has("journalpostId")) {
                            OpprettetJournalpostResponse(
                                responseBody["journalpostId"].textValue(),
                                responseBody["journalpostferdigstilt"].asBoolean()
                            )
                        } else {
                            joarkIntegrationException("Klarte ikke å arkivere barnebrillevedtak. Feilet med response <$response>")
                        }
                    }

                    else -> {
                        joarkIntegrationException("Klarte ikke å arkivere barnebrillevedtak. Feilet med response <$response>")
                    }
                }
            }.onFailure {
                logger.error(it) { it.message }
                throw it
            }
        }.getOrThrow()
    }

    suspend fun feilregistrerJournalpost(journalpostId: String) {
        client.patch("$baseUrl/journalpost/$journalpostId/feilregistrer/feilregistrerSakstilknytning")
            .expect(HttpStatusCode.OK)
    }

    suspend fun opprettJournalpost() {
        val response: HttpResponse = client.post("$baseUrl/journalpost?forsoekFerdigstill=false") {
            setBody("")
        }
    }

    private suspend fun HttpResponse.expect(expected: HttpStatusCode) = when (status) {
        expected -> Unit
        else -> {
            val body = runCatching { bodyAsText() }.getOrElse { it.message }
            error("Uventet svar fra tjeneste, kall: '${request.method.value} ${request.url}', status: '${status}', body: '$body'")
        }
    }
}

data class OpprettJournalpostRequest(
    val avsenderMottaker: AvsenderMottaker,
    val bruker: Bruker,
    val datoMottatt: LocalDateTime?,
    val dokumenter: List<Dokumenter>?,
    val tema: String,
    val tittel: String,
    val kanal: String,
    val eksternReferanseId: String,
    val journalpostType: String,
)
