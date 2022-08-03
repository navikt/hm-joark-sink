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
        val rows: List<List<String>> = csvReader().readAll(jpFeil)
        rows.forEach { row ->
            logger.info { "jp: ${row.first()}" }
            kotlin.runCatching {
                runBlocking {
                   // joarkClientv2.feilregistrerJournalpostData("")
                }
            }.onFailure {
                logger.warn { "Klarte ikke å feilregistrere jp med id: ${row.first()}" }
            }.onSuccess {
                logger.info { "Feilregistrerte jp med id: ${row.first()}" }
            }
        }



    }
}


val jpFeil = """
    ournalpostId;sakId
    576824388; 121
    576822312; 120
    576821953; 119
    576821865; 118
    576821693; 117
    576821341; 116
    576820391; 115
    576820146; 114
    576819870; 113
    576813463; 112
    576811816; 111
    576806980; 110
    576806677; 109
    576783867; 108
    576781515; 107
    576779990; 106
    576779909; 105
    576779468; 104
    576777828; 103
    576777682; 102
    576777447; 101
    576776423; 100
    576775326; 99
    576775280; 98
    576775116; 97
    576774396; 96
    576774232; 95
    576773171; 94
    576772969; 93
    576772948; 92
    576772920; 91
    576772635; 90
    576772291; 89
    576772319; 88
    576771863; 87
    576769942; 86
    576769694; 85
    576768862; 84
    576768062; 83
    576767503; 82
    576767315; 81
    576767245; 80
    576767268; 79
    576767029; 78
    576764602; 77
    576764499; 76
    576764263; 75
    576764129; 74
    576761678; 73
    576760571; 72
    576759937; 71
    576759920; 70
    576759548; 69
    576758919; 68
    576757226; 67
    576757094; 66
    576754610; 65
    576754132; 64
    576749567; 63
    576749247; 62
    576747216; 61
    576746844; 60
    576745797; 59
    576743805; 58
    576739758; 57
    576739720; 56
    576735315; 55
    576734944; 54
    576734816; 53
    576731897; 52
    576731861; 51
    576731191; 50
    576731131; 49
    576728561; 48
    576728098; 47
    576726685; 46
    576726621; 45
    576722379; 44
    576721560; 43
    576721144; 42
    576719973; 41
    576686615; 40
    576684762; 39
    576682919; 38
    576682817; 37
    576679826; 36
    576678502; 35
    576677729; 34
    576677520; 33
    576676607; 32
    576676222; 31
    576674229; 30
    576673585; 29
    576672698; 28
    576671866; 27
    576671593; 26
    576671031; 25
    576669771; 24
    576668571; 23
    576666170; 22
    576661298; 21
    576657572; 20
    576655114; 19
    576654330; 18
    576652899; 17
    576647970; 16
    576646885; 15
    576646826; 14
    576639958; 13
    576623208; 12
    576620676; 11
    576615721; 10
    576614880; 9
    576611876; 8
    576607238; 7
    576602069; 6
    576601076; 5
    576594640; 4
    576594196; 3
    576586770; 2
    576585055; 1
""".trimIndent()