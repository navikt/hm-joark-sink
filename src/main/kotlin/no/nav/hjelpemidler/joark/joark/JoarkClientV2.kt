package no.nav.hjelpemidler.joark.joark

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.awaitResult
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.httpPatch
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.httpPut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.joark.joark.model.AvsenderMottaker
import no.nav.hjelpemidler.joark.joark.model.Bruker
import no.nav.hjelpemidler.joark.joark.model.Dokumenter
import no.nav.hjelpemidler.joark.joark.model.Dokumentvarianter
import no.nav.hjelpemidler.joark.joark.model.HjelpemidlerDigitalSoknad
import java.util.Base64
import java.util.UUID
import kotlin.collections.ArrayList

private val logger = KotlinLogging.logger {}

class JoarkClientV2(
    private val baseUrl: String,
    private val accesstokenScope: String,
    private val azureClient: AzureClient
) {

    companion object {
        private val objectMapper = ObjectMapper()
        const val DOKUMENT_TITTEL = "Søknad om hjelpemidler"
        const val ID_TYPE = "FNR"
        const val LAND = "NORGE"
        const val BREV_KODE = "NAV 10-07.03"
        const val DOKUMENT_KATEGORI = "SOK"
        const val FIL_TYPE = "PDFA"
        const val VARIANT_FORMAT = "ARKIV"
        const val TEMA = "HJE"
        const val KANAL = "NAV_NO"
        const val JOURNALPOST_TYPE = "INNGAAENDE"
    }

    suspend fun opprettOgFerdigstillJournalføring(
        fnrBruker: String,
        navnAvsender: String,
        soknadId: UUID,
        soknadPdf: ByteArray
    ): OpprettetJournalpostResponse {
        logger.info { "opprett midlertidig journalføring" }

        val requestBody = HjelpemidlerDigitalSoknad(
            AvsenderMottaker(fnrBruker, ID_TYPE, LAND, navnAvsender),
            Bruker(fnrBruker, ID_TYPE),
            hentlistDokumentTilJournalForening(Base64.getEncoder().encodeToString(soknadPdf)),
            TEMA,
            DOKUMENT_TITTEL,
            KANAL,
            soknadId.toString() + "HJE-DIGITAL-SOKNAD_V2",
            JOURNALPOST_TYPE
        )

        val jsonBody = objectMapper.writeValueAsString(requestBody)

        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/opprett-og-ferdigstill".httpPost()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .jsonBody(jsonBody)
                    .awaitObject(
                        object : ResponseDeserializable<JsonNode> {
                            override fun deserialize(content: String): JsonNode {
                                return ObjectMapper().readTree(content)
                            }
                        }
                    )
                    .let {
                        when (it.has("journalpostId")) {
                            true -> OpprettetJournalpostResponse(it["journalpostId"].textValue(), it["journalpostferdigstilt"].booleanValue())
                            false -> throw JoarkException("Klarte ikke å arkivere søknad")
                        }
                    }
            }
                .onFailure {
                    logger.error { it.message }
                }
        }
            .getOrThrow()
    }

    suspend fun oppdaterJournalpostTittel(journalpostNr: Int, tittel: String): String {
        logger.info { "Oppdaterer tittel på journalpost" }

        val requestBody = OppdaterJournalpostRequest(
            tittel = tittel
        )

        val jsonBody = objectMapper.writeValueAsString(requestBody)

        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/$journalpostNr".httpPut()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .jsonBody(jsonBody)
                    .awaitObject(
                        object : ResponseDeserializable<JsonNode> {
                            override fun deserialize(content: String): JsonNode {
                                return ObjectMapper().readTree(content)
                            }
                        }
                    )
                    .let {
                        when (it.has("journalpostId")) {
                            true -> it["journalpostId"].textValue()
                            false -> throw JoarkException("Klarte ikke å oppdatere journalpost")
                        }
                    }
            }
                .onFailure {
                    logger.error { it.message }
                }
        }
            .getOrThrow()
    }

    suspend fun ferdigstillJournalpost(journalpostNr: Int, journalførendeEnhet: Int) {
        logger.info { "Ferdigstiller journalpost" }

        val requestBody = FerdigstillJournalpostRequest(
            journalfoerendeEnhet = journalførendeEnhet
        )

        val jsonBody = objectMapper.writeValueAsString(requestBody)

        withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/$journalpostNr/ferdigstill".httpPatch()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .jsonBody(jsonBody)

                    .awaitResult(
                        object : ResponseDeserializable<JsonNode> {
                            override fun deserialize(content: String): JsonNode {
                                return ObjectMapper().readTree(content)
                            }
                        }
                    )
            }
                .onSuccess {

                    val fuelError = it.component2()
                    if (fuelError != null) {
                        throw fuelError.exception
                    }
                    logger.info { "suksess" }
                }
                .onFailure {
                    logger.error { it.message }
                    throw JoarkExceptionV2(it.message.toString())
                }
                .getOrThrow()
        }
    }

    private fun hentlistDokumentTilJournalForening(soknadPdf: String): List<Dokumenter> {
        val dokuments = ArrayList<Dokumenter>()
        dokuments.add(forbredeHjelpemidlerDokument(soknadPdf))
        return dokuments
    }

    private fun forbredeHjelpemidlerDokument(soknadPdf: String): Dokumenter {
        val dokumentVariants = ArrayList<Dokumentvarianter>()
        dokumentVariants.add(forbredeHjelpemidlerDokumentVariant(soknadPdf))
        return Dokumenter(BREV_KODE, DOKUMENT_KATEGORI, dokumentVariants, "Søknad om hjelpemidler")
    }

    private fun forbredeHjelpemidlerDokumentVariant(soknadPdf: String): Dokumentvarianter =
        Dokumentvarianter("hjelpemidlerdigitalsoknad.pdf", FIL_TYPE, VARIANT_FORMAT, soknadPdf)
}

internal class JoarkExceptionV2(msg: String) : RuntimeException(msg)

data class OppdaterJournalpostRequest(
    val tittel: String
)

data class FerdigstillJournalpostRequest(
    val journalfoerendeEnhet: Int
)

data class OpprettetJournalpostResponse(
    val journalpostNr: String,
    val ferdigstilt: Boolean
)
