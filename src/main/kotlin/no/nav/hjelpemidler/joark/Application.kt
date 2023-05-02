package no.nav.hjelpemidler.joark

import io.ktor.client.engine.cio.CIO
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.dokarkiv.DokarkivClient
import no.nav.hjelpemidler.http.openid.azureADClient
import no.nav.hjelpemidler.joark.joark.JoarkClientV1
import no.nav.hjelpemidler.joark.joark.JoarkClientV2
import no.nav.hjelpemidler.joark.joark.JoarkClientV4
import no.nav.hjelpemidler.joark.pdf.PdfClient
import no.nav.hjelpemidler.joark.service.JoarkDataSink
import no.nav.hjelpemidler.joark.service.JoarkService
import no.nav.hjelpemidler.joark.service.barnebriller.FeilregistrerBarnebrillerJournalpost
import no.nav.hjelpemidler.joark.service.barnebriller.OpprettOgFerdigstillBarnebrillerJournalpost
import no.nav.hjelpemidler.joark.service.barnebriller.OpprettOgFerdigstillBarnebrillevedtakJournalpost
import no.nav.hjelpemidler.joark.service.barnebriller.ResendBarnebrillerJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.FeilregistrerFerdigstillJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.MerkAvvistBestilling
import no.nav.hjelpemidler.joark.service.hotsak.OppdaterOgFerdigstillJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.OpprettMottattJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.OpprettOgFerdigstillJournalpost
import no.nav.hjelpemidler.saf.SafClient
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

fun main() {
    log.info {
        "Gjeldende miljø: ${Environment.current}"
    }
    val engine = CIO.create()
    val azureADClient = azureADClient(engine) {
        cache(leeway = 10.seconds) {
            maximumSize = 10
        }
    }
    val dokarkivClient = DokarkivClient(
        baseUrl = Configuration.JOARK_BASEURL,
        scope = Configuration.JOARK_SCOPE,
        azureADClient = azureADClient,
        engine
    )
    val safClient = SafClient(azureADClient = azureADClient)
    val pdfClient = PdfClient(
        baseUrl = Configuration.PDF_BASEURL,
        engine
    )

    val joarkClient = JoarkClientV1(
        baseUrl = Configuration.JOARK_PROXY_BASEURL,
        scope = Configuration.JOARK_PROXY_SCOPE,
        azureADClient = azureADClient,
        engine
    )
    val joarkClientV2 = JoarkClientV2(
        baseUrl = Configuration.JOARK_PROXY_BASEURL,
        scope = Configuration.JOARK_PROXY_SCOPE,
        azureADClient = azureADClient,
        engine
    )
    val joarkClientV4 = JoarkClientV4(
        baseUrl = Configuration.JOARK_BASEURL,
        scope = Configuration.JOARK_SCOPE,
        azureADClient = azureADClient,
        engine
    )

    val joarkService = JoarkService(dokarkivClient, safClient)

    RapidApplication.create(no.nav.hjelpemidler.configuration.Configuration.current)
        .apply {
            register(statusListener)
            JoarkDataSink(this, pdfClient, joarkClient)

            // Hotsak
            OpprettOgFerdigstillJournalpost(this, pdfClient, joarkClientV2)
            FeilregistrerFerdigstillJournalpost(this, joarkService)
            OpprettMottattJournalpost(this, pdfClient, joarkClient, joarkService)
            MerkAvvistBestilling(this, joarkClientV2)
            OppdaterOgFerdigstillJournalpost(this, dokarkivClient)

            // Barnebriller
            OpprettOgFerdigstillBarnebrillerJournalpost(this, pdfClient, joarkClientV2)
            FeilregistrerBarnebrillerJournalpost(this, joarkService)
            ResendBarnebrillerJournalpost(this, pdfClient, joarkClientV2)
            OpprettOgFerdigstillBarnebrillevedtakJournalpost(this, joarkClientV4)
        }
        .start()
}

val statusListener = object : RapidsConnection.StatusListener {
    override fun onReady(rapidsConnection: RapidsConnection) {
    }
}
