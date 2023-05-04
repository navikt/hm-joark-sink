package no.nav.hjelpemidler.joark.test

import io.mockk.mockk
import io.mockk.slot
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.dokarkiv.DokarkivClient
import no.nav.hjelpemidler.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.joark.pdf.PdfClient
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.saf.SafClient
import kotlin.random.Random

abstract class TestSupport {
    val pdfClientMock = mockk<PdfClient>()
    val dokarkivClientMock = mockk<DokarkivClient>()
    val safClientMock = mockk<SafClient>()

    val journalpostService = JournalpostService(
        pdfClient = pdfClientMock,
        dokarkivClient = dokarkivClientMock,
        safClient = safClientMock
    )

    val rapid = TestRapid().apply {
        configure()
    }

    val pdf = Random.nextBytes(8)

    val opprettJournalpostRequestSlot = slot<OpprettJournalpostRequest>()
    val forsøkFerdigstillSlot = slot<Boolean>()

    abstract fun TestRapid.configure()
}
