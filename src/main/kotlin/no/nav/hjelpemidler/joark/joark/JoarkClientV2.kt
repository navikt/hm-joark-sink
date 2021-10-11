package no.nav.hjelpemidler.joark.joark

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.coroutines.awaitString
import com.github.kittinunf.fuel.httpPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.joark.joark.model.AvsenderMottaker
import no.nav.hjelpemidler.joark.joark.model.Bruker
import no.nav.hjelpemidler.joark.joark.model.Dokumenter
import no.nav.hjelpemidler.joark.joark.model.Dokumentvarianter
import no.nav.hjelpemidler.joark.joark.model.OpprettOgFerdigstillJournalpostRequest
import no.nav.hjelpemidler.joark.joark.model.Sak
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
        soknadPdf: ByteArray,
        sakId: String
    ): OpprettetJournalpostResponse {
        logger.info { "opprett og ferdigstill journalføring" }

        val requestBody = OpprettOgFerdigstillJournalpostRequest(
            AvsenderMottaker(fnrBruker, ID_TYPE, LAND, navnAvsender),
            Bruker(fnrBruker, ID_TYPE),
            hentlistDokumentTilJournalForening(Base64.getEncoder().encodeToString(soknadPdf)),
            TEMA,
            DOKUMENT_TITTEL,
            KANAL,
            soknadId.toString() + "HJE-DIGITAL-SOKNAD",
            JOURNALPOST_TYPE,
            "9999",
            Sak(
                fagsakId = sakId,
                fagsaksystem = "HJELPEMIDLER",
                sakstype = "FAGSAK"
            )
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
                            true -> OpprettetJournalpostResponse(
                                it["journalpostId"].textValue(),
                                it["journalpostferdigstilt"].asBoolean()
                            )
                            false -> throw JoarkException("Klarte ikke å arkivere søknad")
                        }
                    }
            }
                .onFailure {
                    logger.error { it.message }
                    throw it
                }
        }
            .getOrThrow()
    }

    suspend fun feilregistrerJournalpostData(
        journalpostNr: String
    ): String {
        logger.info { "feilregistrer sakstilknytning på journalpost" }

        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/journalpost/$journalpostNr/feilregistrer/feilregistrerSakstilknytning".httpPost()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .awaitString()
                    .let {
                        journalpostNr
                    }
            }
                .onFailure {
                    logger.error { it.message }
                    throw it
                }
        }
            .getOrThrow()
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

data class OpprettetJournalpostResponse(
    val journalpostNr: String,
    val ferdigstilt: Boolean
)
