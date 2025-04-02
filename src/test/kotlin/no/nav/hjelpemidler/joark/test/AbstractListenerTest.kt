package no.nav.hjelpemidler.joark.test

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.mockk.mockk
import io.mockk.slot
import no.nav.hjelpemidler.joark.dokarkiv.DokarkivClient
import no.nav.hjelpemidler.joark.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.joark.pdf.FørstesidegeneratorClient
import no.nav.hjelpemidler.joark.pdf.PdfGeneratorClient
import no.nav.hjelpemidler.joark.pdf.SøknadApiClient
import no.nav.hjelpemidler.joark.pdf.SøknadPdfGeneratorClient
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.saf.SafClient
import no.nav.hjelpemidler.serialization.jackson.valueToJson
import kotlin.random.Random

abstract class AbstractListenerTest() {
    private val rapid: TestRapid = TestRapid()

    val dokarkivClientMock = mockk<DokarkivClient>()
    val førstesidegeneratorClientMock = mockk<FørstesidegeneratorClient>()
    val søknadPdfGeneratorClientMock = mockk<SøknadPdfGeneratorClient>()
    val pdfGeneratorClientMock = mockk<PdfGeneratorClient>()
    val safClientMock = mockk<SafClient>()
    val søknadApiClientMock = mockk<SøknadApiClient>()

    private val journalpostService = JournalpostService(
        dokarkivClient = dokarkivClientMock,
        førstesidegeneratorClient = førstesidegeneratorClientMock,
        søknadPdfGeneratorClient = søknadPdfGeneratorClientMock,
        pdfGeneratorClient = pdfGeneratorClientMock,
        safClient = safClientMock,
        søknadApiClient = søknadApiClientMock,
    )

    constructor(block: (RapidsConnection, JournalpostService) -> Unit) : this() {
        block(rapid, journalpostService)
    }

    fun configure(block: (RapidsConnection, JournalpostService) -> Unit) {
        block(rapid, journalpostService)
    }

    val pdf = Random.nextBytes(8)

    val opprettJournalpostRequestSlot = slot<OpprettJournalpostRequest>()
    val forsøkFerdigstillSlot = slot<Boolean>()

    fun sendTestMessage(vararg pairs: Pair<String, Any?>) {
        rapid.sendTestMessage(valueToJson(mapOf(*pairs)))
    }
}
