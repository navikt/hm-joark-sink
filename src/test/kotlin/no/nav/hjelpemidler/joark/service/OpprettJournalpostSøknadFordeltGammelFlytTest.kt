package no.nav.hjelpemidler.joark.service

import io.kotest.inspectors.shouldForExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import no.nav.hjelpemidler.joark.dokarkiv.models.JournalpostOpprettet
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.domain.Vedlegg
import no.nav.hjelpemidler.joark.test.AbstractListenerTest
import no.nav.hjelpemidler.joark.test.assertSoftly
import no.nav.hjelpemidler.joark.test.shouldHaveCaptured
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class OpprettJournalpostSøknadFordeltGammelFlytTest : AbstractListenerTest(::OpprettJournalpostSøknadFordeltGammelFlyt) {
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
        } returns JournalpostOpprettet("1", setOf("1"), false)
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
            "soknadGjelder" to søknadGjelder,
            "vedlegg" to emptyList<Vedlegg>(),
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
