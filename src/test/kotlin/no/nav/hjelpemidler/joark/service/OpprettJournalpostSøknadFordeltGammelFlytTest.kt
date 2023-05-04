package no.nav.hjelpemidler.joark.service

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.dokarkiv.models.OpprettJournalpostResponse
import no.nav.hjelpemidler.joark.test.TestSupport
import org.intellij.lang.annotations.Language
import kotlin.test.BeforeTest
import kotlin.test.Test

class OpprettJournalpostSøknadFordeltGammelFlytTest : TestSupport() {
    private val søknadId = "aa929fac-b949-4509-a25c-069fa7513d2f"

    override fun TestRapid.configure() {
        OpprettJournalpostSøknadFordeltGammelFlyt(this, journalpostService)
    }

    @BeforeTest
    fun setUp() {
        coEvery {
            pdfClientMock.genererSøknadPdf(any())
        } returns pdf
        coEvery {
            dokarkivClientMock.opprettJournalpost(
                capture(opprettJournalpostRequestSlot),
                capture(forsøkFerdigstillSlot)
            )
        } returns OpprettJournalpostResponse("1")
    }


    @Test
    fun `Oppretter journalpost for søknad fordelt til gammel flyt`() {
        rapid.sendTestMessage(message)
        val opprettJournalpostRequest = opprettJournalpostRequestSlot.captured
        opprettJournalpostRequest.tittel shouldBe Dokumenttype.SØKNAD_OM_HJELPEMIDLER.tittel
        opprettJournalpostRequest.dokumenter shouldHaveSize 1
        opprettJournalpostRequest.eksternReferanseId shouldBe "${søknadId}HJE-DIGITAL-SOKNAD"
        opprettJournalpostRequest.journalfoerendeEnhet.shouldBeNull()
        forsøkFerdigstillSlot.captured.shouldBeFalse()
    }

    @Language("JSON")
    private val message = """{
        "eventName": "hm-søknadFordeltGammelFlyt",
        "fodselNrBruker": "10101012345",
        "navnBruker": "test",
        "soknadId": "$søknadId",
        "soknad": {},
        "soknadGjelder": "test"
    }""".trimIndent()
}
