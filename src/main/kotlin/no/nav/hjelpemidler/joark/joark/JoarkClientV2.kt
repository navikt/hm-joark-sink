package no.nav.hjelpemidler.joark.joark

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.joark.Configuration
import no.nav.hjelpemidler.joark.Configuration.pdf
import no.nav.hjelpemidler.joark.joark.model.AvsenderMottaker
import no.nav.hjelpemidler.joark.joark.model.Bruker
import no.nav.hjelpemidler.joark.joark.model.Dokumenter
import no.nav.hjelpemidler.joark.joark.model.Dokumentvarianter
import no.nav.hjelpemidler.joark.joark.model.OmdøpAvvistBestillingRequest
import no.nav.hjelpemidler.joark.joark.model.OmdøpDokument
import no.nav.hjelpemidler.joark.joark.model.OpprettOgFerdigstillJournalpostMedMottattDatoRequest
import no.nav.hjelpemidler.joark.joark.model.OpprettOgFerdigstillJournalpostRequest
import no.nav.hjelpemidler.joark.joark.model.Sak
import no.nav.hjelpemidler.joark.service.hotsak.BehovsmeldingType
import java.util.Base64
import java.util.UUID

private val logger = KotlinLogging.logger {}

class JoarkClientV2(
    private val baseUrl: String = Configuration.joark.baseUrl,
    private val accesstokenScope: String = Configuration.joark.joarkScope,
    private val azureClient: AzureClient
) {

    private val client = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
        expectSuccess = false
    }

    companion object {
        const val ID_TYPE = "FNR"
        const val LAND = "NORGE"
        const val BREV_KODE_SOK = "NAV 10-07.03"
        const val BREV_KODE_BEST = "NAV 10-07.05"
        const val BREV_KODE_BARNEBRILLE = "NAV 10-07.03" // TODO: bytt til riktig
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

    private val opprettOfFerdigstillUrl = "$baseUrl$OPPRETT_OG_FERDIGSTILL_URL_PATH"

    suspend fun opprettOgFerdigstillJournalføring(
        fnrBruker: String,
        navnAvsender: String,
        soknadId: UUID,
        soknadPdf: ByteArray,
        sakId: String,
        dokumentTittel: String,
        behovsmeldingType: BehovsmeldingType
    ): OpprettetJournalpostResponse {
        logger.info { "opprett og ferdigstill journalføring $dokumentTittel" }

        val requestBody = OpprettOgFerdigstillJournalpostRequest(
            AvsenderMottaker(fnrBruker, ID_TYPE, LAND, navnAvsender),
            Bruker(fnrBruker, ID_TYPE),
            hentlistDokumentTilJournalForening(
                behovsmeldingType,
                dokumentTittel,
                Base64.getEncoder().encodeToString(soknadPdf)
            ),
            TEMA,
            if (behovsmeldingType == BehovsmeldingType.BESTILLING) JOURNALPOSTBESKRIVELSE_BEST else JOURNALPOSTBESKRIVELSE_SOK,
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
                val response: io.ktor.client.statement.HttpResponse = client.post(opprettOfFerdigstillUrl) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    body = requestBody
                }

                when (response.status) {
                    HttpStatusCode.Created, HttpStatusCode.Conflict -> {
                        val responseBody = response.receive<JsonNode>()
                        if (responseBody.has("journalpostId")) {
                            OpprettetJournalpostResponse(
                                responseBody["journalpostId"].textValue(),
                                responseBody["journalpostferdigstilt"].asBoolean()
                            )
                        } else {
                            throw JoarkException("Klarte ikke å arkivere søknad. Feilet med response <$response>")
                        }
                    }
                    else -> {
                        throw JoarkException("Klarte ikke å arkivere søknad. Feilet med response <$response>")
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
        navnAvsender: String
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
                val response: io.ktor.client.statement.HttpResponse = client.post(opprettOfFerdigstillUrl) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    body = requestBody
                }

                when (response.status) {
                    HttpStatusCode.Created, HttpStatusCode.Conflict -> {
                        if (response.status == HttpStatusCode.Conflict) {
                            logger.warn { "Duplikatvarsel ved opprettelse av jp med sakId ${requestBody.sak.fagsakId}" }
                        }
                        val responseBody = response.receive<JsonNode>()
                        if (responseBody.has("journalpostId")) {
                            OpprettetJournalpostResponse(
                                responseBody["journalpostId"].textValue(),
                                responseBody["journalpostferdigstilt"].asBoolean()
                            )
                        } else {
                            throw JoarkException("Klarte ikke å arkivere søknad. Feilet med response <$response>")
                        }
                    }
                    else -> {
                        throw JoarkException("Klarte ikke å arkivere søknad. Feilet med response <$response>")
                    }
                }
            }.onFailure {
                logger.error(it) { it.message }
                throw it
            }
        }.getOrThrow()
    }

    suspend fun feilregistrerJournalpostData(
        journalpostNr: String
    ): String {
        logger.info { "feilregistrer sakstilknytning på journalpost" }

        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                val response: io.ktor.client.statement.HttpResponse =
                    client.post("$baseUrl/journalpost/$journalpostNr/feilregistrer/feilregistrerSakstilknytning") {
                        contentType(ContentType.Application.Json)
                        header(
                            HttpHeaders.Authorization,
                            "Bearer ${azureClient.getToken(accesstokenScope).accessToken}"
                        )
                    }

                when (response.status) {
                    HttpStatusCode.BadRequest -> {
                        val resp = response.receive<JsonNode>()

                        if (resp.has("message") && resp.get("message")
                                .textValue() == "Saksrelasjonen er allerede feilregistrert"
                        ) {
                            logger.info { "Forsøkte å feilregistrere en journalpost som allerede er feilregistrert: " + journalpostNr }
                            return@withContext journalpostNr
                        } else {
                            throw RuntimeException("Feil ved feilregsitrering av journalpost: " + journalpostNr)
                        }
                    }
                    HttpStatusCode.Conflict -> {
                        logger.info { "Conflict - skjer sannsynligvis ikke for dette kallet:  " + journalpostNr }
                        journalpostNr
                    }
                    HttpStatusCode.OK -> {
                        journalpostNr
                    }
                    else -> {
                        throw RuntimeException("Feil ved feilregsitrering av journalpost: " + journalpostNr)
                    }
                }
            }
                .onFailure {
                    logger.error { it.message }
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
        datoMottatt: String
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
                            "barnebrille-${sakId}.pdf",
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
                val response: io.ktor.client.statement.HttpResponse = client.post(opprettOfFerdigstillUrl) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    body = requestBody
                }

                when (response.status) {
                    HttpStatusCode.Created, HttpStatusCode.Conflict -> {
                        if (response.status == HttpStatusCode.Conflict) {
                            logger.warn { "Duplikatvarsel ved opprettelse av jp med sakId ${requestBody.sak.fagsakId}" }
                        }
                        val responseBody = response.receive<JsonNode>()
                        if (responseBody.has("journalpostId")) {
                            OpprettetJournalpostResponse(
                                responseBody["journalpostId"].textValue(),
                                responseBody["journalpostferdigstilt"].asBoolean()
                            )
                        } else {
                            throw JoarkException("Klarte ikke å arkivere søknad. Feilet med response <$response>")
                        }
                    }
                    else -> {
                        throw JoarkException("Klarte ikke å arkivere søknad. Feilet med response <$response>")
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
                    tittel = "$prefix${it.second}",
                )
            },
        )

        withContext(Dispatchers.IO) {
            kotlin.runCatching {
                val response: io.ktor.client.statement.HttpResponse = client.put("$omdøpAvvistBestillingUrl/$joarkRef") {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    body = requestBody
                }

                when (response.status) {
                    HttpStatusCode.Created, HttpStatusCode.Conflict -> {
                        if (response.status == HttpStatusCode.Conflict) {
                            logger.warn("HttpStatusCode.Conflict ved omdøping av jp med joarkRef=$joarkRef")
                        }
                        val responseBody = response.receive<JsonNode>()
                        // FIXME: Remove before prod.
                        logger.info("DEBUG: Response body: ${jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(responseBody)}")
                    }
                    else -> {
                        throw JoarkException("Klarte ikke å omdøpe avvist bestilling. Feilet med response <$response>")
                    }
                }
            }.onFailure {
                logger.error(it) { "Feilet i å omdøpe avvist bestilling i joark, ignorerer" }
            }.getOrNull()
        }
    }

    private fun hentlistDokumentTilJournalForening(
        behovsmeldingType: BehovsmeldingType,
        dokumentTittel: String,
        soknadPdf: String
    ): List<Dokumenter> {
        val dokuments = ArrayList<Dokumenter>()
        dokuments.add(forbredeHjelpemidlerDokument(behovsmeldingType, dokumentTittel, soknadPdf))
        return dokuments
    }

    private fun forbredeHjelpemidlerDokument(
        behovsmeldingType: BehovsmeldingType,
        dokumentTittel: String,
        soknadPdf: String
    ): Dokumenter {
        val dokumentVariants = ArrayList<Dokumentvarianter>()
        dokumentVariants.add(forbredeHjelpemidlerDokumentVariant(behovsmeldingType, soknadPdf))
        return Dokumenter(
            if (behovsmeldingType == BehovsmeldingType.BESTILLING) BREV_KODE_BEST else BREV_KODE_SOK,
            if (behovsmeldingType == BehovsmeldingType.BESTILLING) null else DOKUMENT_KATEGORI_SOK,
            dokumentVariants,
            dokumentTittel
        )
    }

    private fun forbredeHjelpemidlerDokumentVariant(
        behovsmeldingType: BehovsmeldingType,
        soknadPdf: String
    ): Dokumentvarianter =
        Dokumentvarianter(
            if (behovsmeldingType == BehovsmeldingType.BESTILLING) "hjelpemidlerdigitalbestilling.pdf" else "hjelpemidlerdigitalsoknad.pdf",
            FIL_TYPE,
            VARIANT_FORMAT,
            soknadPdf
        )
}

internal class JoarkExceptionV2(msg: String) : RuntimeException(msg)

data class OpprettetJournalpostResponse(
    val journalpostNr: String,
    val ferdigstilt: Boolean
)
