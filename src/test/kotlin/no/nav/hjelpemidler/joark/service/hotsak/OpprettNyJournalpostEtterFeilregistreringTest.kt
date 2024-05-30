package no.nav.hjelpemidler.joark.service.hotsak

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.hjelpemidler.joark.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.joark.dokarkiv.models.OpprettJournalpostResponse
import no.nav.hjelpemidler.joark.domain.Sakstype
import no.nav.hjelpemidler.joark.jsonMapper
import no.nav.hjelpemidler.joark.test.TestSupport
import no.nav.hjelpemidler.joark.test.assertSoftly
import no.nav.hjelpemidler.joark.test.shouldHaveCaptured
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class OpprettNyJournalpostEtterFeilregistreringTest : TestSupport() {
    private val nå = LocalDateTime.now()
    private val fnrBruker = "10101012345"
    private val journalpostId = "2"

    override fun TestRapid.configure() {
        OpprettNyJournalpostEtterFeilregistrering(this, journalpostService)
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
        } returns OpprettJournalpostResponse("3", false)
    }

    @Test
    fun `Oppretter ny journalpost hvis sakstype er SØKNAD`() {
        sendTestMessage(sakstype = Sakstype.SØKNAD)

        opprettJournalpostRequestSlot.assertSoftly {
            journalposttype shouldBe OpprettJournalpostRequest.Journalposttype.INNGAAENDE
            journalfoerendeEnhet shouldBe null
        }
        forsøkFerdigstillSlot shouldHaveCaptured false
    }

    @Test
    fun `Oppretter ny journalpost hvis sakstype er BESTILLING`() {
        sendTestMessage(sakstype = Sakstype.BESTILLING)

        opprettJournalpostRequestSlot.assertSoftly {
            journalposttype shouldBe OpprettJournalpostRequest.Journalposttype.INNGAAENDE
            journalfoerendeEnhet shouldBe null
        }
        forsøkFerdigstillSlot shouldHaveCaptured false
    }

    @Test
    fun `Kopierer journalpost hvis sakstype er BARNEBRILLER`() {
        coEvery {
            dokarkivClientMock.kopierJournalpost(journalpostId, any())
        } returns "3"

        sendTestMessage(sakstype = Sakstype.BARNEBRILLER)

        coVerify(exactly = 1) { dokarkivClientMock.kopierJournalpost(journalpostId, any()) }
    }

    private fun sendTestMessage(sakstype: Sakstype) =
        sendTestMessage(
            "eventName" to "hm-feilregistrerteSakstilknytningForJournalpost",
            "soknadId" to UUID.randomUUID(),
            "soknadJson" to jsonMapper.createObjectNode(),
            "sakId" to "1",
            "fnrBruker" to fnrBruker,
            "navnBruker" to "test",
            "mottattDato" to nå,
            "dokumentBeskrivelse" to "test",
            "enhet" to "1000",
            "sakstype" to sakstype,
            "nyJournalpostId" to journalpostId,
            "navIdent" to "X123456",
            "valgteÅrsaker" to jsonMapper.createArrayNode(),
        )
}
