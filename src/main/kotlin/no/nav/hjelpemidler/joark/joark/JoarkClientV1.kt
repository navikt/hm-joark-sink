package no.nav.hjelpemidler.joark.joark

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.RequestTimeout
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.dokarkiv.models.AvsenderMottaker
import no.nav.hjelpemidler.dokarkiv.models.Bruker
import no.nav.hjelpemidler.dokarkiv.models.Dokument
import no.nav.hjelpemidler.dokarkiv.models.DokumentVariant
import no.nav.hjelpemidler.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.openID
import no.nav.hjelpemidler.joark.service.hotsak.Sakstype
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

private val logger = KotlinLogging.logger {}

class JoarkClientV1(
    private val baseUrl: String,
    private val scope: String,
    private val azureADClient: OpenIDClient,
    engine: HttpClientEngine = CIO.create(),
) {
    companion object {
        const val DOKUMENT_TITTEL_SOK = "Søknad om hjelpemidler"
        const val DOKUMENT_TITTEL_BEST = "Bestilling av hjelpemidler"
        const val LAND = "NORGE"
        const val BREV_KODE_SOK = "NAV 10-07.03"
        const val BREV_KODE_BEST = "NAV 10-07.05"
        const val DOKUMENT_KATEGORI_SOK = "SOK"
        const val FIL_TYPE = "PDFA"

    }

    private val client = createHttpClient(engine) {
        expectSuccess = false
        defaultRequest {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
        }
        install(HttpRequestRetry) {
            maxRetries = 5
            retryIf { _, response ->
                response.status == RequestTimeout
            }
            exponentialDelay()
        }
        openID(scope, azureADClient)
    }

    suspend fun arkiverSoknad(
        fnrBruker: String,
        navnAvsender: String,
        dokumentTittel: String,
        søknadId: UUID,
        søknadPdf: ByteArray,
        sakstype: Sakstype,
        eksternRefId: String = søknadId.toString() + "HJE-DIGITAL-SOKNAD",
        mottattDato: LocalDateTime? = null,
    ): String {
        logger.info { "Arkiverer søknad" }
        val requestBody = OpprettJournalpostRequest(
            avsenderMottaker = AvsenderMottaker(fnrBruker, AvsenderMottaker.IdType.FNR, navnAvsender),
            bruker = Bruker(fnrBruker, Bruker.IdType.FNR),
            datoMottatt = null, // mottattDato, fixme
            dokumenter = lagDokumentliste(
                sakstype,
                dokumentTittel,
                Base64.getEncoder().encodeToString(søknadPdf)
            ),
            tema = "HJE",
            tittel = if (sakstype == Sakstype.BESTILLING) DOKUMENT_TITTEL_BEST else DOKUMENT_TITTEL_SOK,
            kanal = "NAV_NO",
            eksternReferanseId = eksternRefId,
            journalposttype = OpprettJournalpostRequest.Journalposttype.INNGAAENDE
        )

        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                val response: HttpResponse = client.post(baseUrl) {
                    setBody(requestBody)
                }

                when (response.status) {
                    HttpStatusCode.Created, HttpStatusCode.Conflict -> {
                        if (response.status == HttpStatusCode.Conflict) {
                            logger.warn { "Duplikatvarsel ved opprettelse av jp med soknadId $søknadId" }
                        }
                        val responseBody = response.body<JsonNode>()
                        if (responseBody.has("journalpostId")) {
                            responseBody["journalpostId"].textValue()
                        } else {
                            joarkIntegrationException("Klarte ikke å arkivere søknad $søknadId. Feilet med response <$response>")
                        }
                    }

                    else -> {
                        joarkIntegrationException("Klarte ikke å arkivere søknad $søknadId. Feilet med response <$response>")
                    }
                }
            }.onFailure {
                logger.error(it) { it.message }
                throw it
            }
        }.getOrThrow()
    }

    private fun lagDokumentliste(
        sakstype: Sakstype,
        dokumentTittel: String,
        søknadPdf: String,
    ): List<Dokument> =
        listOf(lagDokumenter(sakstype, dokumentTittel, søknadPdf))

    private fun lagDokumenter(
        sakstype: Sakstype,
        dokumentTittel: String,
        søknadPdf: String,
    ): Dokument =
        Dokument(
            brevkode = if (sakstype == Sakstype.BESTILLING) BREV_KODE_BEST else BREV_KODE_SOK,
            // if (sakstype == Sakstype.BESTILLING) null else DOKUMENT_KATEGORI_SOK, fixme
            dokumentvarianter = listOf(lagDokumentvarianter(sakstype, søknadPdf)),
            tittel = dokumentTittel
        )

    private fun lagDokumentvarianter(
        sakstype: Sakstype,
        søknadPdf: String,
    ): DokumentVariant =
        DokumentVariant(
            filtype = FIL_TYPE,
            // if (sakstype == Sakstype.BESTILLING) "hjelpemidlerdigitalbestilling.pdf" else "hjelpemidlerdigitalsoknad.pdf",
            fysiskDokument = listOf(søknadPdf.toByteArray()),
            variantformat = "ARKIV",
        )
}
