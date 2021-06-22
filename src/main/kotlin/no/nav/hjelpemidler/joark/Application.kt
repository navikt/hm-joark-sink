package no.nav.hjelpemidler.joark

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.hjelpemidler.joark.joark.AzureClient
import no.nav.hjelpemidler.joark.joark.JoarkClient
import no.nav.hjelpemidler.joark.pdf.PdfClient
import no.nav.hjelpemidler.joark.service.JoarkDataSink
import no.nav.hjelpemidler.joark.service.JoarkDataSinkQ1
import no.nav.hjelpemidler.joark.wiremock.WiremockServer

fun main() {

    if (Configuration.application.profile == Profile.LOCAL) {
        WiremockServer(Configuration).startServer()
    }

    val pdfClient = PdfClient(Configuration.pdf.baseUrl)
    val azureClient = AzureClient(
        tenantUrl = "${Configuration.azure.tenantBaseUrl}/${Configuration.azure.tenantId}",
        clientId = Configuration.azure.clientId,
        clientSecret = Configuration.azure.clientSecret
    )
    val joarkClient = JoarkClient(
        baseUrl = Configuration.joark.baseUrl,
        accesstokenScope = Configuration.joark.joarkScope,
        azureClient = azureClient
    )

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.rapidApplication))
        .build().apply {
            JoarkDataSink(this, pdfClient, joarkClient)
            if (Configuration.application.profile == Profile.DEV) {
                JoarkDataSinkQ1(
                    this, pdfClient,
                    JoarkClient(
                        baseUrl = Configuration.joark.baseUrl + "-q1",
                        accesstokenScope = Configuration.joark.joarkScope,
                        azureClient = azureClient
                    )
                )
            }
        }.start()
}
