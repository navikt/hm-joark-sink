package no.nav.hjelpemidler.joark.joark

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import com.github.tomakehurst.wiremock.client.WireMock.created
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class JoarkClientV2Test {

    private val server = WireMockServer(WireMockConfiguration.options().dynamicPort())
    private val azureClient = mockk<AzureClient>(relaxed = true)
    private lateinit var joarkClientV2: JoarkClientV2

    @BeforeAll
    fun setup() {
        server.start()
        configureFor("localhost", server.port())
        joarkClientV2 = JoarkClientV2(baseUrl = server.baseUrl(), azureClient = azureClient)
    }

    @AfterAll
    fun teardown() {
        server.stop()
    }

    @Test
    fun `skal returnere OpprettetJournalpostResponse dersom Joark returnerer 201 Created`() = runBlocking {
        stubFor(
            post(JoarkClientV2.OPPRETT_OG_FERDIGSTILL_URL_PATH)
                .willReturn(
                    created()
                        .withHeader("Content-Type", "application/json")
                        .withBody(journalpostapiResponse)
                )
        )

        val response = joarkClientV2.opprettOgFerdigstillJournalføring(
            fnrBruker = "12345678910",
            navnAvsender = "Mikke Mus",
            soknadId = UUID.randomUUID(),
            soknadPdf = ByteArray(0),
            sakId = "1234",
            dokumentTittel = "Søknad om: rullator"
        )
        val expected = OpprettetJournalpostResponse("467010363", true)
        assertEquals(expected, response)
    }

    @Test
    fun `skal returnere OpprettetJournalpostResponse dersom Joark returnerer 409 Conflict`() = runBlocking {
        stubFor(
            post(JoarkClientV2.OPPRETT_OG_FERDIGSTILL_URL_PATH).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withStatus(409)
                    .withBody(journalpostapiResponse)
            )
        )

        val response = joarkClientV2.opprettOgFerdigstillJournalføring(
            fnrBruker = "12345678910",
            navnAvsender = "Mikke Mus",
            soknadId = UUID.randomUUID(),
            soknadPdf = ByteArray(0),
            sakId = "1234",
            dokumentTittel = "Søknad om: rullator"
        )
        val expected = OpprettetJournalpostResponse("467010363", true)
        assertEquals(expected, response)
    }
}

// https://dokarkiv-q1.dev.intern.nav.no/swagger-ui/index.html#/journalpostapi/opprettJournalpost
private val journalpostapiResponse =
    """
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
