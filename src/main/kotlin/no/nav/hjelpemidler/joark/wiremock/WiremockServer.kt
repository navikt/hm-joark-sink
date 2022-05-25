package no.nav.hjelpemidler.joark.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.hjelpemidler.joark.Configuration

internal class WiremockServer(private val configuration: Configuration) {

    fun startServer() {
        val wiremockServer = WireMockServer(9111)
        wiremockServer
            .stubFor(
                WireMock.post(WireMock.urlPathMatching("/${configuration.azure.tenantId}/oauth2/v2.0/token"))
                    .willReturn(
                        WireMock.aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """{
                        "token_type": "Bearer",
                        "expires_in": 3599,
                        "access_token": "1234abc"
                    }"""
                            )
                    )
            )
        wiremockServer
            .stubFor(
                WireMock.post(WireMock.urlPathMatching("/dokarkiv"))
                    .willReturn(
                        WireMock.aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                                {
                                    "journalpostId": "12345"
                                }
                                      """
                            )
                    )
            )

        wiremockServer
            .stubFor(
                WireMock.post(WireMock.urlPathMatching("/dokarkiv/opprett-og-ferdigstill"))
                    .willReturn(
                        WireMock.aResponse().withStatus(201)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                                {
                                    "journalpostId": "12345",
                                    "journalpostferdigstilt": "true"
                                }
                                      """
                            )
                    )
            )

        wiremockServer.start()
    }
}
