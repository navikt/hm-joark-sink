package no.nav.hjelpemidler.joark

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.configuration.LocalEnvironment
import no.nav.hjelpemidler.http.openid.azureADClient
import no.nav.hjelpemidler.joark.joark.JoarkClient
import no.nav.hjelpemidler.joark.joark.JoarkClientV2
import no.nav.hjelpemidler.joark.joark.JoarkClientV3
import no.nav.hjelpemidler.joark.joark.JoarkClientV4
import no.nav.hjelpemidler.joark.pdf.PdfClient
import no.nav.hjelpemidler.joark.service.JoarkDataSink
import no.nav.hjelpemidler.joark.service.barnebriller.FeilregistrerBarnebrillerJournalpost
import no.nav.hjelpemidler.joark.service.barnebriller.OpprettOgFerdigstillBarnebrillerJournalpost
import no.nav.hjelpemidler.joark.service.barnebriller.OpprettOgFerdigstillBarnebrillevedtakJournalpost
import no.nav.hjelpemidler.joark.service.barnebriller.ResendBarnebrillerJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.FeilregistrerFerdigstiltJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.MerkAvvistBestilling
import no.nav.hjelpemidler.joark.service.hotsak.OppdaterOgFerdigstillJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.OpprettMottattJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.OpprettOgFerdigstillJournalpost
import no.nav.hjelpemidler.joark.wiremock.WiremockServer
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

fun main() {
    if (Environment.current is LocalEnvironment) {
        logger.info { "Starter WiremockServer..." }
        WiremockServer(Configuration).startServer()
    }

    val pdfClient = PdfClient(Configuration.pdf.baseUrl)
    val azureAdClient = azureADClient {
        cache(leeway = 10.seconds)
    }
    val joarkClient = JoarkClient(
        baseUrl = Configuration.joark.proxyBaseUrl,
        scope = Configuration.joark.proxyScope,
        azureAdClient = azureAdClient
    )
    val joarkClientV2 = JoarkClientV2(
        baseUrl = Configuration.joark.proxyBaseUrl,
        scope = Configuration.joark.proxyScope,
        azureAdClient = azureAdClient
    )
    val joarkClientV3 = JoarkClientV3(
        baseUrl = Configuration.joark.baseUrl,
        scope = Configuration.joark.scope,
        azureADClient = azureADClient()
    )
    val joarkClientV4 = JoarkClientV4(
        baseUrl = Configuration.joark.baseUrl,
        scope = Configuration.joark.scope,
        azureAdClient = azureAdClient
    )

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.rapidApplication))
        .build().apply {
            register(statusListener)
            JoarkDataSink(this, pdfClient, joarkClient)

            // Hotsak
            OpprettOgFerdigstillJournalpost(this, pdfClient, joarkClientV2)
            FeilregistrerFerdigstiltJournalpost(this, joarkClientV2)
            OpprettMottattJournalpost(this, pdfClient, joarkClient)
            MerkAvvistBestilling(this, joarkClientV2)
            OppdaterOgFerdigstillJournalpost(this, joarkClientV3)

            // Barnebriller
            OpprettOgFerdigstillBarnebrillerJournalpost(this, pdfClient, joarkClientV2)
            FeilregistrerBarnebrillerJournalpost(this, joarkClientV2)
            ResendBarnebrillerJournalpost(this, pdfClient, joarkClientV2)
            OpprettOgFerdigstillBarnebrillevedtakJournalpost(this, joarkClientV4)
        }.start()
}

val statusListener = object : RapidsConnection.StatusListener {
    override fun onReady(rapidsConnection: RapidsConnection) {
    }
}
