package no.nav.hjelpemidler.joark.service.hotsak

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import no.nav.hjelpemidler.joark.dokarkiv.models.Bruker
import no.nav.hjelpemidler.joark.dokarkiv.models.JournalpostOpprettet
import no.nav.hjelpemidler.joark.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.joark.dokarkiv.models.Sak
import no.nav.hjelpemidler.joark.test.AbstractListenerTest
import no.nav.hjelpemidler.joark.test.assertSoftly
import no.nav.hjelpemidler.joark.test.shouldHaveCaptured
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class SakOpprettetOpprettOgFerdigstillJournalpostTest : AbstractListenerTest(::SakOpprettetOpprettOgFerdigstillJournalpost) {
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
        } returns JournalpostOpprettet("1", setOf("1"), true)
    }

    @Test
    fun `Skal opprette og ferdigstille journalpost for sak opprettet i Hotsak`() {
        val sakId = "1"
        val fnrBruker = "10101012345"
        sendTestMessage(
            "eventName" to "hm-sakOpprettet",
            "soknadId" to UUID.randomUUID(),
            "soknadGjelder" to "test",
            "sakId" to sakId,
            "fnrBruker" to fnrBruker,
            "navnBruker" to "test",
            "behovsmeldingType" to "SØKNAD",
        )

        opprettJournalpostRequestSlot.assertSoftly {
            journalposttype shouldBe OpprettJournalpostRequest.Journalposttype.INNGAAENDE
            journalfoerendeEnhet shouldBe "9999"
            bruker?.id shouldBe fnrBruker
            bruker?.idType shouldBe Bruker.IdType.FNR
            sak?.fagsakId shouldBe sakId
            sak?.fagsaksystem shouldBe Sak.Fagsaksystem.HJELPEMIDLER
            sak?.sakstype shouldBe Sak.Sakstype.FAGSAK
        }
        forsøkFerdigstillSlot shouldHaveCaptured true
    }
}
