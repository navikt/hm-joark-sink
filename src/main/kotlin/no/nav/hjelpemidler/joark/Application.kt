package no.nav.hjelpemidler.joark

import io.ktor.client.engine.cio.CIO
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.dokarkiv.DokarkivClient
import no.nav.hjelpemidler.http.openid.azureADClient
import no.nav.hjelpemidler.joark.pdf.PdfClient
import no.nav.hjelpemidler.joark.service.OpprettJournalpostForSøknadFordeltGammelFlyt
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.joark.service.barnebriller.FeilregistrerJournalpostBarnebriller
import no.nav.hjelpemidler.joark.service.barnebriller.OpprettOgFerdigstillJournalpostBarnebriller
import no.nav.hjelpemidler.joark.service.hotsak.VedtakBarnebrillerOpprettOgFerdigstillJournalpost
import no.nav.hjelpemidler.joark.service.barnebriller.ResendJournalpostBarnebriller
import no.nav.hjelpemidler.joark.service.hotsak.SakTilbakeførtFeilregistrerJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.BestillingAvvistOppdaterJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.JournalpostJournalførtOppdaterOgFerdigstillJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.OpprettNyJournalpostEtterFeilregistrering
import no.nav.hjelpemidler.joark.service.hotsak.SakOpprettetOpprettOgFerdigstillJournalpost
import no.nav.hjelpemidler.saf.SafClient
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

fun main() {
    log.info {
        "Gjeldende miljø: ${Environment.current}"
    }

    // Clients
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
        engine,
    )
    val safClient = SafClient(
        azureADClient = azureADClient,
        engine = engine,
    )
    val pdfClient = PdfClient(
        baseUrl = Configuration.PDF_BASEURL,
        engine = engine,
    )

    // Services
    val journalpostService = JournalpostService(
        pdfClient = pdfClient,
        dokarkivClient = dokarkivClient,
        safClient = safClient,
    )

    RapidApplication.create(no.nav.hjelpemidler.configuration.Configuration.current)
        .apply {
            register(statusListener)
            OpprettJournalpostForSøknadFordeltGammelFlyt(this, journalpostService)

            // Hotsak
            SakOpprettetOpprettOgFerdigstillJournalpost(this, journalpostService)
            SakTilbakeførtFeilregistrerJournalpost(this, journalpostService)
            OpprettNyJournalpostEtterFeilregistrering(this, journalpostService)
            BestillingAvvistOppdaterJournalpost(this, journalpostService)
            JournalpostJournalførtOppdaterOgFerdigstillJournalpost(this, dokarkivClient)

            // Barnebriller
            OpprettOgFerdigstillJournalpostBarnebriller(this, journalpostService)
            FeilregistrerJournalpostBarnebriller(this, journalpostService)
            ResendJournalpostBarnebriller(this, journalpostService)
            VedtakBarnebrillerOpprettOgFerdigstillJournalpost(this, journalpostService)
        }
        .start()
}

val statusListener = object : RapidsConnection.StatusListener {
    override fun onReady(rapidsConnection: RapidsConnection) {
    }
}
