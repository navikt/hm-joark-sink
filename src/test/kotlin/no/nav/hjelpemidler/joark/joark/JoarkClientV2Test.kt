package no.nav.hjelpemidler.joark.joark

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.joark.Configuration
import no.nav.hjelpemidler.joark.service.hotsak.BehovsmeldingType
import no.nav.hjelpemidler.joark.test.TestOpenIDClient
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class JoarkClientV2Test {
    @Test
    fun `skal returnere OpprettetJournalpostResponse dersom Joark returnerer 201 Created`() = runBlocking {
        val client = setupClient {
            respondJson(journalpostApiResponse, HttpStatusCode.Created)
        }
        val response = client.opprettOgFerdigstillJournalføring(
            fnrBruker = "12345678910",
            navnAvsender = "Mikke Mus",
            soknadId = UUID.randomUUID(),
            soknadPdf = "".toByteArray(),
            sakId = "1234",
            dokumentTittel = "Søknad om: rullator",
            behovsmeldingType = BehovsmeldingType.SØKNAD
        )

        val expected = OpprettetJournalpostResponse("467010363", true)

        assertEquals(expected, response)
    }

    @Test
    fun `skal returnere OpprettetJournalpostResponse dersom Joark returnerer 409 Conflict`() = runBlocking {
        val client = setupClient {
            respondJson(journalpostApiResponse, HttpStatusCode.Conflict)
        }
        val response = client.opprettOgFerdigstillJournalføring(
            fnrBruker = "12345678910",
            navnAvsender = "Mikke Mus",
            soknadId = UUID.randomUUID(),
            soknadPdf = "".toByteArray(),
            sakId = "1234",
            dokumentTittel = "Søknad om: rullator",
            behovsmeldingType = BehovsmeldingType.SØKNAD
        )

        val expected = OpprettetJournalpostResponse("467010363", true)

        assertEquals(expected, response)
    }

    private fun setupClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        JoarkClientV2(
            baseUrl = Configuration.JOARK_PROXY_BASEURL,
            scope = Configuration.JOARK_PROXY_SCOPE,
            azureADClient = TestOpenIDClient(),
            engine = MockEngine {
                handler(this, it)
            }
        )

    private fun MockRequestHandleScope.respondJson(content: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(content, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
}

// https://dokarkiv-q1.dev.intern.nav.no/swagger-ui/index.html#/journalpostapi/opprettJournalpost
@Language("JSON")
private val journalpostApiResponse = """
    {
        "dokumenter": [
            {
                "dokumentInfoId": "123"
            }
        ],
        "journalpostId": "467010363",
        "journalpostferdigstilt": true
    }
""".trimIndent()
