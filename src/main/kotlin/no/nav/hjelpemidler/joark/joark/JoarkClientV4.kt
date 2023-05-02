package no.nav.hjelpemidler.joark.joark

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
import no.nav.hjelpemidler.joark.joark.model.Dokument
import no.nav.hjelpemidler.joark.joark.model.Dokumentvariant
import no.nav.hjelpemidler.joark.joark.model.OpprettOgFerdigstillJournalpostRequest
import no.nav.hjelpemidler.joark.joark.model.Sak
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
        const val DOKUMENT_KATEGORI_VED = "VED"
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
            avsenderMottaker = AvsenderMottaker(fnr, "FNR", "NORGE", navnAvsender),
            bruker = Bruker(fnr, "FNR"),
            dokumenter = listOf(
                Dokument(
                    brevkode = "vedtaksbrev_barnebriller",
                    dokumentvarianter = listOf(
                        Dokumentvariant(
                            filnavn = "barnebrille_vedtak.pdf",
                            filtype = "PDFA",
                            variantformat = "ARKIV",
                            fysiskDokument = Base64.getEncoder().encodeToString(pdf)
                        )
                    ),
                    tittel = dokumentTittel
                )
            ),
            tema = "HJE",
            tittel = "Vedtak for barnebrille",
            kanal = "NAV_NO",
            eksternReferanseId = sakId + "BARNEBRILLEVEDTAK",
            journalpostType = "UTGAAENDE",
            journalfoerendeEnhet = "9999",
            sak = Sak(
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

}
