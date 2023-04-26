package no.nav.hjelpemidler.joark.joark

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import no.nav.hjelpemidler.joark.joark.model.OmdøpAvvistBestillingRequest
import no.nav.hjelpemidler.joark.joark.model.OmdøpDokument
import no.nav.hjelpemidler.joark.joark.model.OpprettOgFerdigstillJournalpostMedMottattDatoRequest
import no.nav.hjelpemidler.joark.joark.model.OpprettOgFerdigstillJournalpostRequest
import no.nav.hjelpemidler.joark.joark.model.Sak
import no.nav.hjelpemidler.joark.service.hotsak.Sakstype
import java.util.Base64
import java.util.UUID

private val logger = KotlinLogging.logger {}

class JoarkClientV2(
    private val baseUrl: String,
    private val scope: String,
    private val azureADClient: OpenIDClient,
    engine: HttpClientEngine = CIO.create(),
) {
    companion object {
        const val ID_TYPE = "FNR"
        const val LAND = "NORGE"
        const val BREV_KODE_SOK = "NAV 10-07.03"
        const val BREV_KODE_BEST = "NAV 10-07.05"
        const val BREV_KODE_TILSKUDD_VED_KJØP_AV_BRILLER_TIL_BARN = "NAV 10-07.34"
        const val BREV_KODE_TILSKUDD_VED_KJØP_AV_BRILLER_TIL_BARN_ETTERSENDING = "NAVe 10-07.34"
        const val DOKUMENT_KATEGORI_SOK = "SOK"
        const val FIL_TYPE = "PDFA"
        const val VARIANT_FORMAT = "ARKIV"
        const val TEMA = "HJE"
        const val KANAL = "NAV_NO"
        const val JOURNALPOST_TYPE = "INNGAAENDE"
        const val JOURNALPOSTBESKRIVELSE_SOK = "Søknad om hjelpemidler"
        const val JOURNALPOSTBESKRIVELSE_BEST = "Bestilling av hjelpemidler"
        const val JOURNALPOSTBESKRIVELSE_BARNEBRILLE = "Vedtak for barnebrille" // TODO: bytt til riktig
        const val OPPRETT_OG_FERDIGSTILL_URL_PATH = "/opprett-og-ferdigstill"
        const val OMDØP_AVVIST_BESTILLING_URL_PATH = "/omdop-avvist-bestilling"
    }

    private val client = createHttpClient(engine) {
        expectSuccess = false
        defaultRequest {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
        }
        openID(scope, azureADClient)
    }

    private val opprettOfFerdigstillUrl = "$baseUrl$OPPRETT_OG_FERDIGSTILL_URL_PATH"

    suspend fun opprettOgFerdigstillJournalføring(
        fnrBruker: String,
        navnAvsender: String,
        soknadId: UUID,
        soknadPdf: ByteArray,
        sakId: String,
        dokumentTittel: String,
        sakstype: Sakstype,
    ): OpprettetJournalpostResponse {
        logger.info { "opprett og ferdigstill journalføring $dokumentTittel" }

        val requestBody = OpprettOgFerdigstillJournalpostRequest(
            AvsenderMottaker(fnrBruker, ID_TYPE, LAND, navnAvsender),
            Bruker(fnrBruker, ID_TYPE),
            hentlistDokumentTilJournalForening(
                sakstype,
                dokumentTittel,
                Base64.getEncoder().encodeToString(soknadPdf)
            ),
            TEMA,
            if (sakstype == Sakstype.BESTILLING) JOURNALPOSTBESKRIVELSE_BEST else JOURNALPOSTBESKRIVELSE_SOK,
            KANAL,
            soknadId.toString() + "HOTSAK",
            JOURNALPOST_TYPE,
            "9999",
            Sak(
                fagsakId = sakId,
                fagsaksystem = "HJELPEMIDLER",
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
                            logger.warn { "Duplikatvarsel ved opprettelse av jp med sakId $sakId og søknadId $soknadId" }
                        }
                        val responseBody = response.body<JsonNode>()
                        if (responseBody.has("journalpostId")) {
                            OpprettetJournalpostResponse(
                                responseBody["journalpostId"].textValue(),
                                responseBody["journalpostferdigstilt"].asBoolean()
                            )
                        } else {
                            joarkIntegrationException("Klarte ikke å arkivere søknad $soknadId. Feilet med response <$response>")
                        }
                    }

                    else -> {
                        joarkIntegrationException("Klarte ikke å arkivere søknad $soknadId. Feilet med response <$response>")
                    }
                }
            }.onFailure {
                logger.error(it) { it.message }
                throw it
            }
        }.getOrThrow()
    }

    suspend fun opprettOgFerdigstillJournalføringBarnebriller(
        fnr: String,
        orgnr: String,
        // soknadId: UUID,
        pdf: ByteArray,
        sakId: String,
        dokumentTittel: String,
        navnAvsender: String,
    ): OpprettetJournalpostResponse {
        logger.info { "opprett og ferdigstill journalføring $dokumentTittel" }

        val requestBody = OpprettOgFerdigstillJournalpostRequest(
            AvsenderMottaker(fnr, ID_TYPE, LAND, navnAvsender),
            Bruker(fnr, ID_TYPE),
            listOf(
                Dokumenter(
                    dokumentKategori = DOKUMENT_KATEGORI_SOK,
                    dokumentvarianter = listOf(
                        Dokumentvarianter(
                            "barnebrille.pdf",
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
            sakId + "BARNEBRILLEAPI",
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
                            joarkIntegrationException("Klarte ikke å arkivere søknad. Feilet med response <$response>")
                        }
                    }

                    else -> {
                        joarkIntegrationException("Klarte ikke å arkivere søknad. Feilet med response <$response>")
                    }
                }
            }.onFailure {
                logger.error(it) { it.message }
                throw it
            }
        }.getOrThrow()
    }

    suspend fun feilregistrerJournalpostData(
        journalpostNr: String,
    ): String {
        logger.info { "feilregistrer sakstilknytning på journalpost" }

        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                val response: HttpResponse =
                    client.post("$baseUrl/journalpost/$journalpostNr/feilregistrer/feilregistrerSakstilknytning")

                when (response.status) {
                    HttpStatusCode.BadRequest -> {
                        val resp = response.body<JsonNode>()

                        if (resp.has("message") && resp.get("message")
                                .textValue() == "Saksrelasjonen er allerede feilregistrert"
                        ) {
                            logger.info { "Forsøkte å feilregistrere en journalpost som allerede er feilregistrert: $journalpostNr" }
                            return@withContext journalpostNr
                        } else {
                            joarkIntegrationException("Feil ved feilregistrering av journalpost: $journalpostNr")
                        }
                    }

                    HttpStatusCode.Conflict -> {
                        logger.info { "Conflict - skjer sannsynligvis ikke for dette kallet:  $journalpostNr" }
                        journalpostNr
                    }

                    HttpStatusCode.OK -> {
                        journalpostNr
                    }

                    else -> {
                        joarkIntegrationException("Feil ved feilregistrering av journalpost: $journalpostNr")
                    }
                }
            }
                .onFailure {
                    logger.error(it) { it.message }
                    throw it
                }.getOrThrow()
        }
    }

    suspend fun rekjørJournalføringBarnebriller(
        fnr: String,
        orgnr: String,
        // soknadId: UUID,
        pdf: ByteArray,
        sakId: String,
        dokumentTittel: String,
        navnAvsender: String,
        datoMottatt: String,
    ): OpprettetJournalpostResponse {
        logger.info { "rekjør journalføring barnebriller $dokumentTittel" }

        val requestBody = OpprettOgFerdigstillJournalpostMedMottattDatoRequest(
            AvsenderMottaker(fnr, ID_TYPE, LAND, navnAvsender),
            Bruker(fnr, ID_TYPE),
            listOf(
                Dokumenter(
                    dokumentKategori = DOKUMENT_KATEGORI_SOK,
                    dokumentvarianter = listOf(
                        Dokumentvarianter(
                            "barnebrille-$sakId.pdf",
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
            "RE_" + sakId + "BARNEBRILLEAPI",
            JOURNALPOST_TYPE,
            "9999",
            Sak(
                fagsakId = sakId,
                fagsaksystem = "BARNEBRILLER",
                sakstype = "FAGSAK"
            ),
            datoMottatt
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
                            joarkIntegrationException("Klarte ikke å arkivere søknad. Feilet med response <$response>")
                        }
                    }

                    else -> {
                        joarkIntegrationException("Klarte ikke å arkivere søknad. Feilet med response <$response>")
                    }
                }
            }.onFailure {
                logger.error(it) { it.message }
                throw it
            }
        }.getOrThrow()
    }

    private val omdøpAvvistBestillingUrl = "$baseUrl$OMDØP_AVVIST_BESTILLING_URL_PATH"

    suspend fun omdøpAvvistBestilling(
        joarkRef: String,
        tittel: String,
        dokumenter: List<Pair<String, String>>,
    ) {
        logger.info("Omdøper avvist bestilling: joarkRef=$joarkRef gammelTittel=\"$tittel\" gamleDokumenter=<$dokumenter>")

        val prefix = "Avvist: "
        val requestBody = OmdøpAvvistBestillingRequest(
            tittel = "$prefix$tittel",
            dokumenter = dokumenter.map {
                OmdøpDokument(
                    dokumentInfoId = it.first,
                    tittel = "$prefix${it.second}"
                )
            }
        )

        withContext(Dispatchers.IO) {
            kotlin.runCatching {
                val response: HttpResponse =
                    client.put("$omdøpAvvistBestillingUrl/$joarkRef") {
                        setBody(requestBody)
                    }
                when (response.status) {
                    HttpStatusCode.OK, HttpStatusCode.Created, HttpStatusCode.Conflict -> {}
                    else -> {
                        joarkIntegrationException("Klarte ikke å omdøpe avvist bestilling. Feilet med response <$response>")
                    }
                }
            }.onFailure {
                logger.error(it) { "Feilet i å omdøpe avvist bestilling i joark, ignorerer" }
            }.getOrNull()
        }
    }

    private fun hentlistDokumentTilJournalForening(
        sakstype: Sakstype,
        dokumentTittel: String,
        soknadPdf: String,
    ): List<Dokumenter> {
        val dokuments = ArrayList<Dokumenter>()
        dokuments.add(forbredeHjelpemidlerDokument(sakstype, dokumentTittel, soknadPdf))
        return dokuments
    }

    private fun forbredeHjelpemidlerDokument(
        sakstype: Sakstype,
        dokumentTittel: String,
        soknadPdf: String,
    ): Dokumenter {
        val dokumentVariants = ArrayList<Dokumentvarianter>()
        dokumentVariants.add(forbredeHjelpemidlerDokumentVariant(sakstype, soknadPdf))
        return Dokumenter(
            if (sakstype == Sakstype.BESTILLING) BREV_KODE_BEST else BREV_KODE_SOK,
            if (sakstype == Sakstype.BESTILLING) null else DOKUMENT_KATEGORI_SOK,
            dokumentVariants,
            dokumentTittel
        )
    }

    private fun forbredeHjelpemidlerDokumentVariant(
        sakstype: Sakstype,
        soknadPdf: String,
    ): Dokumentvarianter =
        Dokumentvarianter(
            if (sakstype == Sakstype.BESTILLING) "hjelpemidlerdigitalbestilling.pdf" else "hjelpemidlerdigitalsoknad.pdf",
            FIL_TYPE,
            VARIANT_FORMAT,
            soknadPdf
        )
}

data class OpprettetJournalpostResponse(
    val journalpostNr: String,
    val ferdigstilt: Boolean,
)
