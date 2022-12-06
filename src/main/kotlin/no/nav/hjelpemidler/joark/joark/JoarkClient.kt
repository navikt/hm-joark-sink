package no.nav.hjelpemidler.joark.joark

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.httpPost
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.joark.joark.model.AvsenderMottaker
import no.nav.hjelpemidler.joark.joark.model.Bruker
import no.nav.hjelpemidler.joark.joark.model.Dokumenter
import no.nav.hjelpemidler.joark.joark.model.Dokumentvarianter
import no.nav.hjelpemidler.joark.joark.model.HjelpemidlerDigitalSoknad
import no.nav.hjelpemidler.joark.service.hotsak.BehovsmeldingType
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

private val logger = KotlinLogging.logger {}

class JoarkClient(
    private val baseUrl: String,
    private val accesstokenScope: String,
    private val azureClient: AzureClient
) {

    companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

        private val ktorClient = HttpClient(CIO) {
            install(JsonFeature) {
                serializer = JacksonSerializer()
            }
            expectSuccess = false
        }

        const val DOKUMENT_TITTEL_SOK = "Søknad om hjelpemidler"
        const val DOKUMENT_TITTEL_BEST = "Bestilling av hjelpemidler"
        const val ID_TYPE = "FNR"
        const val LAND = "NORGE"
        const val BREV_KODE_SOK = "NAV 10-07.03"
        const val BREV_KODE_BEST = "NAV 10-07.05"
        const val DOKUMENT_KATEGORI_SOK = "SOK"
        const val FIL_TYPE = "PDFA"
        const val VARIANT_FORMAT = "ARKIV"
        const val TEMA = "HJE"
        const val KANAL = "NAV_NO"
        const val JOURNALPOST_TYPE = "INNGAAENDE"
    }

    suspend fun arkiverSoknad(
        fnrBruker: String,
        navnAvsender: String,
        dokumentTittel: String,
        soknadId: UUID,
        soknadPdf: ByteArray,
        behovsmeldingType: BehovsmeldingType,
        eksternRefId: String = soknadId.toString() + "HJE-DIGITAL-SOKNAD",
        mottattDato: LocalDateTime? = null
    ): String {
        logger.info { "Arkiverer søknad" }

        val requestBody = HjelpemidlerDigitalSoknad(
            AvsenderMottaker(fnrBruker, ID_TYPE, LAND, navnAvsender),
            Bruker(fnrBruker, ID_TYPE),
            datoMottatt = mottattDato,
            hentlistDokumentTilJournalForening(
                behovsmeldingType,
                dokumentTittel,
                Base64.getEncoder().encodeToString(soknadPdf)
            ),
            TEMA,
            if (behovsmeldingType == BehovsmeldingType.BESTILLING) DOKUMENT_TITTEL_BEST else DOKUMENT_TITTEL_SOK,
            KANAL,
            eksternRefId,
            JOURNALPOST_TYPE
        )

        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                /* "$baseUrl".httpPost().header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .jsonBody(jsonBody).awaitObject(object : ResponseDeserializable<JsonNode> {
                        override fun deserialize(content: String): JsonNode {
                            return objectMapper.readTree(content)
                        }
                    }).let {
                        when (it.has("journalpostId")) {
                            true -> it["journalpostId"].textValue()
                            false -> throw JoarkException("Klarte ikke å arkivere søknad")
                        }
                    } */

                val response: io.ktor.client.statement.HttpResponse = ktorClient.post(baseUrl) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    body = requestBody
                }

                when (response.status) {
                    HttpStatusCode.Created, HttpStatusCode.Conflict -> {
                        if (response.status == HttpStatusCode.Conflict) {
                            logger.warn { "Duplikatvarsel ved opprettelse av jp med soknadId $soknadId" }
                        }
                        val responseBody = response.receive<JsonNode>()
                        if (responseBody.has("journalpostId")) {
                            responseBody["journalpostId"].textValue()
                        } else {
                            throw JoarkException("Klarte ikke å arkivere søknad $soknadId. Feilet med response <$response>")
                        }
                    }
                    else -> {
                        throw JoarkException("Klarte ikke å arkivere søknad $soknadId. Feilet med response <$response>")
                    }
                }
            }.onFailure {
                logger.error { it.message }
            }
        }.getOrThrow()
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

internal class JoarkException(msg: String) : RuntimeException(msg)
