package no.nav.hjelpemidler.joark

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.engine.cio.CIO
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.domain.person.TILLAT_SYNTETISKE_FØDSELSNUMRE
import no.nav.hjelpemidler.http.openid.azureADClient
import no.nav.hjelpemidler.joark.brev.BrevClient
import no.nav.hjelpemidler.joark.brev.BrevService
import no.nav.hjelpemidler.joark.dokarkiv.DokarkivClient
import no.nav.hjelpemidler.joark.pdf.FørstesidegeneratorClient
import no.nav.hjelpemidler.joark.pdf.PdfGeneratorClient
import no.nav.hjelpemidler.joark.pdf.SøknadApiClient
import no.nav.hjelpemidler.joark.pdf.SøknadPdfGeneratorClient
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.joark.service.OpprettJournalpostSøknadFordeltGammelFlyt
import no.nav.hjelpemidler.joark.service.barnebriller.FeilregistrerJournalpostBarnebriller
import no.nav.hjelpemidler.joark.service.barnebriller.OpprettOgFerdigstillJournalpostBarnebriller
import no.nav.hjelpemidler.joark.service.barnebriller.OpprettOgFerdigstillJournalpostBarnebrillerAvvisning
import no.nav.hjelpemidler.joark.service.barnebriller.ResendJournalpostBarnebriller
import no.nav.hjelpemidler.joark.service.hotsak.BestillingAvvistOppdaterJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.BrevsendingOpprettetOpprettOgFerdigstillJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.JournalpostJournalførtOppdaterOgFerdigstillJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.KnyttJournalposterTilNySak
import no.nav.hjelpemidler.joark.service.hotsak.SakAnnulert
import no.nav.hjelpemidler.joark.service.hotsak.SakOpprettetOpprettOgFerdigstillJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.SakOverførtGosysFeilregistrerOgErstattJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.SaksnotatFeilregistrertFeilregistrerJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.SaksnotatOpprettetOpprettOgFerdigstillJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.SaksnotatOverstyrInnsynForJournalpost
import no.nav.hjelpemidler.joark.service.hotsak.VedtakBarnebrillerOpprettOgFerdigstillJournalpost
import no.nav.hjelpemidler.rapids_and_rivers.register
import no.nav.hjelpemidler.saf.SafClient
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

fun main() {
    TILLAT_SYNTETISKE_FØDSELSNUMRE = !Environment.current.isProd

    log.info { "Gjeldende miljø: ${Environment.current}, eventName: ${Configuration.EVENT_NAME}, TILLAT_SYNTETISKE_FØDSELSNUMRE: $TILLAT_SYNTETISKE_FØDSELSNUMRE" }

    // Clients
    val engine = CIO.create()
    val azureADClient = azureADClient(engine) {
        cache(leeway = 10.seconds) {
            maximumSize = 10
        }
    }
    val brevClient = BrevClient(engine)
    val dokarkivClient = DokarkivClient(azureADClient.withScope(Configuration.JOARK_SCOPE), engine)
    val førstesidegeneratorClient =
        FørstesidegeneratorClient(azureADClient.withScope(Configuration.FORSTESIDEGENERATOR_SCOPE), engine)
    val pdfGeneratorClient = PdfGeneratorClient(engine)
    val safClient = SafClient(azureADClient.withScope(Configuration.SAF_SCOPE), engine)
    val søknadApiClient = SøknadApiClient(azureADClient.withScope(Configuration.SOKNAD_API_SCOPE), engine)
    val søknadPdfGeneratorClient = SøknadPdfGeneratorClient(engine)

    // Services
    val journalpostService = JournalpostService(
        dokarkivClient = dokarkivClient,
        førstesidegeneratorClient = førstesidegeneratorClient,
        søknadPdfGeneratorClient = søknadPdfGeneratorClient,
        pdfGeneratorClient = pdfGeneratorClient,
        safClient = safClient,
        søknadApiClient = søknadApiClient,
    )
    val brevService = BrevService(
        brevClient = brevClient,
    )

    RapidApplication.create(no.nav.hjelpemidler.configuration.Configuration.current)
        .apply {
            register(statusListener)
            OpprettJournalpostSøknadFordeltGammelFlyt(this, journalpostService)

            // Hotsak
            BestillingAvvistOppdaterJournalpost(this, journalpostService)
            BrevsendingOpprettetOpprettOgFerdigstillJournalpost(this, journalpostService)
            JournalpostJournalførtOppdaterOgFerdigstillJournalpost(this, journalpostService)
            KnyttJournalposterTilNySak(this, journalpostService)
            SakAnnulert(this, journalpostService)
            SakOpprettetOpprettOgFerdigstillJournalpost(this, journalpostService)
            SakOverførtGosysFeilregistrerOgErstattJournalpost(this, journalpostService)

            // Saksnotater
            register(SaksnotatFeilregistrertFeilregistrerJournalpost(journalpostService))
            register(SaksnotatOpprettetOpprettOgFerdigstillJournalpost(journalpostService))
            register(SaksnotatOverstyrInnsynForJournalpost(journalpostService))

            // Barnebriller
            FeilregistrerJournalpostBarnebriller(this, journalpostService)
            OpprettOgFerdigstillJournalpostBarnebriller(this, journalpostService)
            OpprettOgFerdigstillJournalpostBarnebrillerAvvisning(this, journalpostService, brevService)
            ResendJournalpostBarnebriller(this, journalpostService)
            VedtakBarnebrillerOpprettOgFerdigstillJournalpost(this, journalpostService)
        }
        .start()
}

val statusListener = object : RapidsConnection.StatusListener {
    override fun onReady(rapidsConnection: RapidsConnection) {
    }
}
