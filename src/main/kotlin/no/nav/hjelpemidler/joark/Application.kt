package no.nav.hjelpemidler.joark

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.engine.cio.CIO
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.hjelpemidler.configuration.Environment
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
import no.nav.hjelpemidler.joark.service.hotsak.*
import no.nav.hjelpemidler.saf.SafClient
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import java.util.*
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

fun main() {
    log.info { "Gjeldende miljø: ${Environment.current}" }
    log.info { "Gjeldende event name: ${Configuration.EVENT_NAME}" }

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
        pdfGeneratorClient = pdfGeneratorClient,
        søknadPdfGeneratorClient = søknadPdfGeneratorClient,
        dokarkivClient = dokarkivClient,
        safClient = safClient,
        førstesidegeneratorClient = førstesidegeneratorClient,
        søknadApiClient = søknadApiClient,
    )
    val brevService = BrevService(
        brevClient = brevClient,
    )

    val rapidAccess = RapidAccess()
    RapidApplication.create(no.nav.hjelpemidler.configuration.Configuration.current, builder = devBuilder(rapidAccess))
        .apply {
            register(statusListener)
            OpprettJournalpostSøknadFordeltGammelFlyt(this, journalpostService)

            // Hotsak
            BestillingAvvistOppdaterJournalpost(this, journalpostService)
            BrevsendingOpprettetOpprettOgFerdigstillJournalpost(this, journalpostService)
            JournalførtNotatOpprettetOpprettOgFerdigstillJournalpost(this, journalpostService)
            JournalpostJournalførtOppdaterOgFerdigstillJournalpost(this, journalpostService)
            SakAnnulert(this, journalpostService)
            SakOpprettetOpprettOgFerdigstillJournalpost(this, journalpostService)
            SakTilbakeførtFeilregistrerOgErstattJournalpost(this, journalpostService)
            KnyttJournalposterTilNySak(this, journalpostService)

            // Barnebriller
            FeilregistrerJournalpostBarnebriller(this, journalpostService)
            OpprettOgFerdigstillJournalpostBarnebriller(this, journalpostService)
            OpprettOgFerdigstillJournalpostBarnebrillerAvvisning(this, journalpostService, brevService)
            ResendJournalpostBarnebriller(this, journalpostService)
            VedtakBarnebrillerOpprettOgFerdigstillJournalpost(this, journalpostService)

            rapidAccess.rapidsConnection = this
        }
        .start()
}

val statusListener = object : RapidsConnection.StatusListener {
    override fun onReady(rapidsConnection: RapidsConnection) {
    }
}

data class RapidAccess(
    var rapidsConnection: RapidsConnection? = null,
)
fun devBuilder(rapidAccess: RapidAccess): RapidApplication.Builder.() -> Unit {
    if (!Environment.current.isDev) {
        return {}
    }
    return {
        withKtorModule {
            install(ContentNegotiation) {
                jackson()
            }
            routing {
                post("/internal/test-api") {
                    data class KafkaBody(
                        val eventName: String = "hm-journalført-notat-opprettet",
                        val eventId: UUID = UUID.randomUUID(),
                        val fnrBruker: String = "26848497710",
                        val dokumenttittel: String = "Test notat",
                        val språkkode: String = "bokmaal",
                        val strukturertDokument: JsonNode? = null,
                        val sakId: String,
                        val notatId: String,
                        val opprettetAv: String,
                        val fysiskDokument: ByteArray,
                    )
                    data class RequestBody(
                        val body: KafkaBody,
                        val dryRun: Boolean = true,
                    )
                    val body: RequestBody = call.receive()
                    no.nav.hjelpemidler.joark.log.info { "Received KafkaBody for test (dryRun=${body.dryRun}): ${jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body.body)}" }
                    rapidAccess.rapidsConnection?.let { rapid ->
                        if (!body.dryRun) {
                            rapid.publish(jsonMapper.writeValueAsString(body.body))
                        }
                    }
                }
            }
        }
    }
}
