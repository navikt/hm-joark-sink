package no.nav.hjelpemidler.joark

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.joark.joark.AzureClient
import no.nav.hjelpemidler.joark.joark.JoarkClient
import no.nav.hjelpemidler.joark.joark.JoarkClientV2
import no.nav.hjelpemidler.joark.pdf.PdfClient
import no.nav.hjelpemidler.joark.service.FeilregistrerFerdigstiltJournalpost
import no.nav.hjelpemidler.joark.service.JoarkDataSink
import no.nav.hjelpemidler.joark.service.OpprettMottattJournalpost
import no.nav.hjelpemidler.joark.service.OpprettOgFerdigstillBarnebrillerJournalpost
import no.nav.hjelpemidler.joark.service.OpprettOgFerdigstillJournalpost
import no.nav.hjelpemidler.joark.wiremock.WiremockServer
import java.io.File

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
            OpprettOgFerdigstillJournalpost(this, pdfClient, joarkClientv2)
            FeilregistrerFerdigstiltJournalpost(this, joarkClientv2)
            OpprettMottattJournalpost(this, pdfClient, joarkClient)
            OpprettOgFerdigstillBarnebrillerJournalpost(this, pdfClient, joarkClientv2)
        }.start()
}


val statusListener = object : RapidsConnection.StatusListener {
    override fun onReady(rapidsConnection: RapidsConnection) {

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
        } else if (Configuration.application.profile === Profile.DEV) {
            val rows: List<List<String>> = csvReader().readAll(jpFeilDev)
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

    }
}


val jpFeilDev = """
    453809946; 40
""".trimIndent()

val jpFeil = """
    576824388
    576822312
    576821953
    576821865
    576821693
    576821341
    576820391
    576820146
    576819870
    576813463
    576811816
    576806980
    576806677
    576783867
    576781515
    576779990
    576779909
    576779468
    576777828
    576777682
    576777447
    576776423
    576775326
    576775280
    576775116
    576774396
    576774232
    576773171
    576772969
    576772948
    576772920
    576772635
    576772291
    576772319
    576771863
    576769942
    576769694
    576768862
    576768062
    576767503
    576767315
    576767245
    576767268
    576767029
    576764602
    576764499
    576764263
    576764129
    576761678
    576760571
    576759937
    576759920
    576759548
    576758919
    576757226
    576757094
    576754610
    576754132
    576749567
    576749247
    576747216
    576746844
    576745797
    576743805
    576739758
    576739720
    576735315
    576734944
    576734816
    576731897
    576731861
    576731191
    576731131
    576728561
    576728098
    576726685
    576726621
    576722379
    576721560
    576721144
    576719973
    576686615
    576684762
    576682919
    576682817
    576679826
    576678502
    576677729
    576677520
    576676607
    576676222
    576674229
    576673585
    576672698
    576671866
    576671593
    576671031
    576669771
    576668571
    576666170
    576661298
    576657572
    576655114
    576654330
    576652899
    576647970
    576646885
    576646826
    576639958
    576623208
    576620676
    576615721
    576614880
    576611876
    576607238
    576602069
    576601076
    576594640
    576594196
    576586770
    576585055
""".trimIndent()