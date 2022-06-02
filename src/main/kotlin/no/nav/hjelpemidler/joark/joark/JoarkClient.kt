package no.nav.hjelpemidler.joark.joark

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.httpPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.joark.joark.model.AvsenderMottaker
import no.nav.hjelpemidler.joark.joark.model.Bruker
import no.nav.hjelpemidler.joark.joark.model.Dokumenter
import no.nav.hjelpemidler.joark.joark.model.Dokumentvarianter
import no.nav.hjelpemidler.joark.joark.model.HjelpemidlerDigitalSoknad
import no.nav.hjelpemidler.joark.service.BehovsmeldingType
import java.util.Base64
import java.util.UUID
import kotlin.collections.ArrayList

private val logger = KotlinLogging.logger {}

class JoarkClient(
    private val baseUrl: String,
    private val accesstokenScope: String,
    private val azureClient: AzureClient
) {

    companion object {
        private val objectMapper = ObjectMapper()
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
    ): String {
        logger.info { "Arkiverer søknad" }

        val requestBody = HjelpemidlerDigitalSoknad(
            AvsenderMottaker(fnrBruker, ID_TYPE, LAND, navnAvsender),
            Bruker(fnrBruker, ID_TYPE),
            hentlistDokumentTilJournalForening(behovsmeldingType, dokumentTittel, Base64.getEncoder().encodeToString(soknadPdf)),
            TEMA,
            if (behovsmeldingType == BehovsmeldingType.BESTILLING) DOKUMENT_TITTEL_BEST else DOKUMENT_TITTEL_SOK,
            KANAL,
            eksternRefId,
            JOURNALPOST_TYPE
        )

        val jsonBody = objectMapper.writeValueAsString(requestBody)

        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                "$baseUrl".httpPost().header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
                    .jsonBody(jsonBody).awaitObject(object : ResponseDeserializable<JsonNode> {
                        override fun deserialize(content: String): JsonNode {
                            return ObjectMapper().readTree(content)
                        }
                    }).let {
                        when (it.has("journalpostId")) {
                            true -> it["journalpostId"].textValue()
                            false -> throw JoarkException("Klarte ikke å arkivere søknad")
                        }
                    }
            }.onFailure {
                logger.error { it.message }
            }
        }.getOrThrow()
    }

    private fun hentlistDokumentTilJournalForening(behovsmeldingType: BehovsmeldingType, dokumentTittel: String, soknadPdf: String): List<Dokumenter> {
        val dokuments = ArrayList<Dokumenter>()
        dokuments.add(forbredeHjelpemidlerDokument(behovsmeldingType, dokumentTittel, soknadPdf))
        return dokuments
    }

    private fun forbredeHjelpemidlerDokument(behovsmeldingType: BehovsmeldingType, dokumentTittel: String, soknadPdf: String): Dokumenter {
        val dokumentVariants = ArrayList<Dokumentvarianter>()
        dokumentVariants.add(forbredeHjelpemidlerDokumentVariant(behovsmeldingType, soknadPdf))
        return Dokumenter(
            if (behovsmeldingType == BehovsmeldingType.BESTILLING) BREV_KODE_BEST else BREV_KODE_SOK,
            if (behovsmeldingType == BehovsmeldingType.BESTILLING) null else DOKUMENT_KATEGORI_SOK,
            dokumentVariants,
            dokumentTittel
        )
    }

    private fun forbredeHjelpemidlerDokumentVariant(behovsmeldingType: BehovsmeldingType, soknadPdf: String): Dokumentvarianter =
        Dokumentvarianter(
            if (behovsmeldingType == BehovsmeldingType.BESTILLING) "hjelpemidlerdigitalbestilling.pdf" else "hjelpemidlerdigitalsoknad.pdf",
            FIL_TYPE,
            VARIANT_FORMAT,
            soknadPdf
        )
}

internal class JoarkException(msg: String) : RuntimeException(msg)
