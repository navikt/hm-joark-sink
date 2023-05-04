package no.nav.hjelpemidler.joark.service.barnebriller

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.dokarkiv.models.OpprettJournalpostResponse
import no.nav.hjelpemidler.joark.service.Dokumenttype
import no.nav.hjelpemidler.joark.test.TestSupport
import org.intellij.lang.annotations.Language
import kotlin.test.BeforeTest
import kotlin.test.Test

class OpprettOgFerdigstillJournalpostBarnebrillerTest : TestSupport() {
    override fun TestRapid.configure() {
        OpprettOgFerdigstillJournalpostBarnebriller(this, journalpostService)
    }

    @BeforeTest
    fun setUp() {
        coEvery {
            pdfClientMock.genererBarnebrillePdf(any())
        } returns pdf
        coEvery {
            dokarkivClientMock.opprettJournalpost(
                capture(opprettJournalpostRequestSlot),
                capture(forsøkFerdigstillSlot)
            )
        } returns OpprettJournalpostResponse("1")
    }

    @Test
    fun `Skal lage riktig request for opprettelse av journalpost`() {
        rapid.sendTestMessage(message)
        val opprettJournalpostRequest = opprettJournalpostRequestSlot.captured
        opprettJournalpostRequest.tittel shouldBe Dokumenttype.VEDTAKSBREV_BARNEBRILLER_OPTIKER.tittel
        opprettJournalpostRequest.dokumenter shouldHaveSize 1
        opprettJournalpostRequest.eksternReferanseId shouldBe "1BARNEBRILLEAPI"
        opprettJournalpostRequest.journalfoerendeEnhet shouldBe "9999"
        forsøkFerdigstillSlot.captured.shouldBeTrue()
    }

    @Language("JSON")
    private val message = """{
        "eventName": "hm-barnebrillevedtak-opprettet",
        "fnr": "10101012345",
        "brukersNavn": "test",
        "orgnr":  "123456789",
        "orgNavn": "test",
        "orgAdresse":  "test",
        "navnAvsender": "test",
        "eventId":  "8a330f8c-5af4-4873-843a-32a87b9df76b",
        "opprettetDato": "2023-05-01T12:45:30.000",
        "sakId":  "1",
        "brilleseddel": {},
        "bestillingsdato":  "2023-05-01",
        "bestillingsreferanse": "test",
        "satsBeskrivelse":  "test",
        "satsBeløp": "2475",
        "beløp":  "1200"
    }""".trimIndent()
}
