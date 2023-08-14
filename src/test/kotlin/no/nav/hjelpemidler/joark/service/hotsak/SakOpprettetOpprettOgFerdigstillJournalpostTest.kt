package no.nav.hjelpemidler.joark.service.hotsak

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.joark.dokarkiv.models.Bruker
import no.nav.hjelpemidler.joark.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.joark.dokarkiv.models.OpprettJournalpostResponse
import no.nav.hjelpemidler.joark.dokarkiv.models.Sak
import no.nav.hjelpemidler.joark.jsonMapper
import no.nav.hjelpemidler.joark.test.TestSupport
import no.nav.hjelpemidler.joark.test.assertSoftly
import no.nav.hjelpemidler.joark.test.shouldHaveCaptured
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class SakOpprettetOpprettOgFerdigstillJournalpostTest : TestSupport() {
    override fun TestRapid.configure() {
        SakOpprettetOpprettOgFerdigstillJournalpost(this, journalpostService)
    }

    @BeforeTest
    fun setUp() {
        coEvery {
            pdfClientMock.genererPdfSøknad(any())
        } returns pdf
        coEvery {
            dokarkivClientMock.opprettJournalpost(
                capture(opprettJournalpostRequestSlot),
                capture(forsøkFerdigstillSlot)
            )
        } returns OpprettJournalpostResponse("1")
    }

    @Test
    fun `Skal opprette og ferdigstille journalpost for sak opprettet i Hotsak`() {
        val sakId = "1"
        val fnrBruker = "10101012345"
        sendTestMessage(
            "eventName" to "hm-sakOpprettet",
            "soknadId" to UUID.randomUUID(),
            "soknadJson" to jsonMapper.createObjectNode(),
            "soknadGjelder" to "test",
            "sakId" to sakId,
            "fnrBruker" to fnrBruker,
            "navnBruker" to "test",
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
