package no.nav.hjelpemidler.joark

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.hjelpemidler.joark.joark.AzureClient
import no.nav.hjelpemidler.joark.joark.JoarkClient
import no.nav.hjelpemidler.joark.joark.JoarkClientV2
import no.nav.hjelpemidler.joark.pdf.PdfClient
import no.nav.hjelpemidler.joark.service.FeilregistrerFerdigstiltJournalpost
import no.nav.hjelpemidler.joark.service.JoarkDataSink
import no.nav.hjelpemidler.joark.service.OpprettMottattJournalpost
import no.nav.hjelpemidler.joark.service.OpprettOgFerdigstillJournalpost
import no.nav.hjelpemidler.joark.wiremock.WiremockServer

private val logger = KotlinLogging.logger {}

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
    val joarkClientv2 = JoarkClientV2(
        baseUrl = Configuration.joark.baseUrl,
        accesstokenScope = Configuration.joark.joarkScope,
        azureClient = azureClient
    )

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.rapidApplication))
        .build().apply {
            JoarkDataSink(this, pdfClient, joarkClient)
            if (System.getenv("NAIS_CLUSTER_NAME") == null || System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp") {
                logger.info { "Vi er lokalt eller i dev-gcp" }
                logger.info { "Cluster name: " + System.getenv("NAIS_CLUSTER_NAME") }
                OpprettOgFerdigstillJournalpost(this, pdfClient, joarkClientv2)
                FeilregistrerFerdigstiltJournalpost(this, joarkClientv2)
                OpprettMottattJournalpost(this, pdfClient, joarkClient)
            }
        }.start()
}
