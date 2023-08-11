package no.nav.hjelpemidler.joark

import io.ktor.client.engine.cio.CIO
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.joark.dokarkiv.DokarkivClient
import no.nav.hjelpemidler.joark.pdf.FørstesidegeneratorClient
import no.nav.hjelpemidler.http.openid.azureADClient
import no.nav.hjelpemidler.joark.pdf.PdfClient
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.joark.service.OpprettJournalpostSøknadFordeltGammelFlyt
import no.nav.hjelpemidler.joark.service.barnebriller.FeilregistrerJournalpostBarnebriller
import no.nav.hjelpemidler.joark.service.barnebriller.OpprettOgFerdigstillJournalpostBarnebriller
import no.nav.hjelpemidler.joark.service.barnebriller.ResendJournalpostBarnebriller
import no.nav.hjelpemidler.joark.service.hotsak.BestillingAvvistOppdaterJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.BrevsendingOpprettetOpprettOgFerdigstillJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.JournalpostJournalførtOppdaterOgFerdigstillJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.OpprettNyJournalpostEtterFeilregistrering
import no.nav.hjelpemidler.joark.service.hotsak.SakOpprettetOpprettOgFerdigstillJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.SakTilbakeførtFeilregistrerJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.VedtakBarnebrillerOpprettOgFerdigstillJournalpost
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
        azureADClient = azureADClient,
        engine = engine,
    )
    val safClient = SafClient(
        azureADClient = azureADClient,
        engine = engine,
    )
    val pdfClient = PdfClient(
        engine = engine,
    )
    val førstesidegeneratorClient = FørstesidegeneratorClient(
        azureADClient = azureADClient,
        engine = engine,
    )

    // Services
    val journalpostService = JournalpostService(
        pdfClient = pdfClient,
        dokarkivClient = dokarkivClient,
        safClient = safClient,
        førstesidegeneratorClient = førstesidegeneratorClient,
    )

    RapidApplication.create(no.nav.hjelpemidler.configuration.Configuration.current)
        .apply {
            register(statusListener)
            OpprettJournalpostSøknadFordeltGammelFlyt(this, journalpostService)

            // Hotsak
            BestillingAvvistOppdaterJournalpost(this, journalpostService)
            BrevsendingOpprettetOpprettOgFerdigstillJournalpost(this, journalpostService)
            JournalpostJournalførtOppdaterOgFerdigstillJournalpost(this, journalpostService)
            OpprettNyJournalpostEtterFeilregistrering(this, journalpostService)
            SakOpprettetOpprettOgFerdigstillJournalpost(this, journalpostService)
            SakTilbakeførtFeilregistrerJournalpost(this, journalpostService)

            // Barnebriller
            FeilregistrerJournalpostBarnebriller(this, journalpostService)
            OpprettOgFerdigstillJournalpostBarnebriller(this, journalpostService)
            ResendJournalpostBarnebriller(this, journalpostService)
            VedtakBarnebrillerOpprettOgFerdigstillJournalpost(this, journalpostService)
        }
        .start()
}

val statusListener = object : RapidsConnection.StatusListener {
    override fun onReady(rapidsConnection: RapidsConnection) {
    }
}
