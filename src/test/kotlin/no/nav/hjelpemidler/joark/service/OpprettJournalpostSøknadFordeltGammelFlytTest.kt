package no.nav.hjelpemidler.joark.service

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.inspectors.shouldForExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import no.nav.hjelpemidler.joark.dokarkiv.models.OpprettJournalpostResponse
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.test.TestSupport
import no.nav.hjelpemidler.joark.test.assertSoftly
import no.nav.hjelpemidler.joark.test.shouldHaveCaptured
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class OpprettJournalpostSøknadFordeltGammelFlytTest : TestSupport() {
    override fun TestRapid.configure() {
        OpprettJournalpostSøknadFordeltGammelFlyt(this, journalpostService)
    }

    @BeforeTest
    fun setUp() {
        coEvery {
            søknadApiClientMock.hentPdf(any())
        } returns pdf
        coEvery {
            dokarkivClientMock.opprettJournalpost(
                capture(opprettJournalpostRequestSlot),
                capture(forsøkFerdigstillSlot)
            )
        } returns OpprettJournalpostResponse("1", false)
    }


    @Test
    fun `Oppretter journalpost for søknad fordelt til gammel flyt`() {
        val søknadId = UUID.randomUUID()
        val søknadGjelder = "søknadGjelder"
        sendTestMessage(
            "eventName" to "hm-søknadFordeltGammelFlyt",
            "fodselNrBruker" to "fodselNrBruker",
            "navnBruker" to "test",
            "soknadId" to søknadId,
            "behovsmeldingType" to "SØKNAD",
            "erHast" to false,
            "soknadGjelder" to søknadGjelder
        )
        val dokumenttype = Dokumenttype.SØKNAD_OM_HJELPEMIDLER
        opprettJournalpostRequestSlot.assertSoftly {
            tittel shouldBe dokumenttype.tittel
            dokumenter.shouldForExactly(1) { dokument ->
                dokument.tittel shouldBe søknadGjelder
            }
            eksternReferanseId shouldBe "${søknadId}HJE-DIGITAL-SOKNAD"
            journalfoerendeEnhet shouldBe null
        }
        forsøkFerdigstillSlot shouldHaveCaptured false
    }
}
