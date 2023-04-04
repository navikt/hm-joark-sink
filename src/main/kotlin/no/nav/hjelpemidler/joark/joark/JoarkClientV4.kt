package no.nav.hjelpemidler.joark.joark

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.call.body
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
import no.nav.hjelpemidler.joark.joark.model.Dokumenter
import no.nav.hjelpemidler.joark.joark.model.Dokumentvarianter
import no.nav.hjelpemidler.joark.joark.model.OpprettOgFerdigstillJournalpostRequest
import no.nav.hjelpemidler.joark.joark.model.Sak
import java.util.Base64

private val logger = KotlinLogging.logger {}

class JoarkClientV4(
    baseUrl: String,
    private val scope: String,
    private val azureAdClient: OpenIDClient,
) {
    private val client = createHttpClient {
        expectSuccess = false
        defaultRequest {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
        }
        openID(scope, azureAdClient)
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
                            throw JoarkException("Klarte ikke å arkivere barnebrillevedtak. Feilet med response <$response>")
                        }
                    }

                    else -> {
                        throw JoarkException("Klarte ikke å arkivere barnebrillevedtak. Feilet med response <$response>")
                    }
                }
            }.onFailure {
                logger.error(it) { it.message }
                throw it
            }
        }.getOrThrow()
    }
}
