package no.nav.hjelpemidler.joark

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.joark.joark.AzureClient
import no.nav.hjelpemidler.joark.joark.JoarkClient
import no.nav.hjelpemidler.joark.joark.JoarkClientV2
import no.nav.hjelpemidler.joark.pdf.PdfClient
import no.nav.hjelpemidler.joark.service.JoarkDataSink
import no.nav.hjelpemidler.joark.service.barnebriller.FeilregistrerBarnebrillerJournalpost
import no.nav.hjelpemidler.joark.service.barnebriller.OpprettOgFerdigstillBarnebrillerJournalpost
import no.nav.hjelpemidler.joark.service.barnebriller.ResendBarnebrillerJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.FeilregistrerFerdigstiltJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.MerkAvvistBestilling
import no.nav.hjelpemidler.joark.service.hotsak.OpprettMottattJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.OpprettOgFerdigstillJournalpost
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
        azureClient = azureClient
    )

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.rapidApplication))
        .build().apply {
            register(statusListener)
            JoarkDataSink(this, pdfClient, joarkClient)

            // Hotsak
            OpprettOgFerdigstillJournalpost(this, pdfClient, joarkClientv2)
            FeilregistrerFerdigstiltJournalpost(this, joarkClientv2)
            OpprettMottattJournalpost(this, pdfClient, joarkClient)
            MerkAvvistBestilling(this, joarkClientv2)

            // Barnebriller
            OpprettOgFerdigstillBarnebrillerJournalpost(this, pdfClient, joarkClientv2)
            FeilregistrerBarnebrillerJournalpost(this, joarkClientv2)
            ResendBarnebrillerJournalpost(this, pdfClient, joarkClientv2)
        }.start()
}

val statusListener = object : RapidsConnection.StatusListener {
    override fun onReady(rapidsConnection: RapidsConnection) {
        /*
            val jpFeil = """
            """.trimIndent()

            val azureClient = AzureClient(
                tenantUrl = "${Configuration.azure.tenantBaseUrl}/${Configuration.azure.tenantId}",
                clientId = Configuration.azure.clientId,
                clientSecret = Configuration.azure.clientSecret
            )

            val joarkClientv2 = JoarkClientV2(
                azureClient = azureClient
            )

            logger.info { "App har starta" }

            if (Configuration.application.profile === Profile.PROD) {
                val rows: List<List<String>> = csvReader().readAll(jpFeil)
                rows.forEach { row ->
                    logger.info { "jp: ${row.first()}" }
                    kotlin.runCatching {
                        runBlocking {
                            if (Configuration.application.profile == Profile.PROD) {
                                joarkClientv2.feilregistrerJournalpostData(row.first())
                            }
                        }
                    }.onFailure {
                        logger.warn { "Klarte ikke å feilregistrere jp med id: ${row.first()}" }
                    }.onSuccess {
                        logger.info { "Feilregistrerte jp med id: ${row.first()}" }
                    }
                }
            }
        */
    }
}
