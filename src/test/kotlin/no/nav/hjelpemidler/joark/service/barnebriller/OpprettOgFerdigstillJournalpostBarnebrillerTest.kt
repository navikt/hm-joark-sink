package no.nav.hjelpemidler.joark.service.barnebriller

import io.kotest.inspectors.shouldForExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import no.nav.hjelpemidler.joark.dokarkiv.models.JournalpostOpprettet
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.test.AbstractListenerTest
import no.nav.hjelpemidler.joark.test.assertSoftly
import no.nav.hjelpemidler.joark.test.shouldHaveCaptured
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class OpprettOgFerdigstillJournalpostBarnebrillerTest : AbstractListenerTest(::OpprettOgFerdigstillJournalpostBarnebriller) {
    @BeforeTest
    fun setUp() {
        coEvery {
            søknadPdfGeneratorClientMock.genererPdfBarnebriller(any())
        } returns pdf
        coEvery {
            dokarkivClientMock.opprettJournalpost(
                capture(opprettJournalpostRequestSlot),
                capture(forsøkFerdigstillSlot)
            )
        } returns JournalpostOpprettet("1", setOf("1"), true)
    }

    @Test
    fun `Skal lage riktig request for opprettelse av journalpost`() {
        sendTestMessage(
            "eventName" to "hm-barnebrillevedtak-opprettet",
            "fnr" to "10101012345",
            "brukersNavn" to "test",
            "orgnr" to "123456789",
            "orgNavn" to "test",
            "orgAdresse" to "test",
            "navnAvsender" to "test",
            "eventId" to UUID.randomUUID(),
            "opprettetDato" to LocalDateTime.now(),
            "sakId" to "1",
            "brilleseddel" to jsonMapper.createObjectNode(),
            "bestillingsdato" to "2023-05-01",
            "bestillingsreferanse" to "test",
            "satsBeskrivelse" to "test",
            "satsBeløp" to "2475",
            "beløp" to "1200",
        )
        val dokumenttype = Dokumenttype.KRAV_BARNEBRILLER_OPTIKER
        opprettJournalpostRequestSlot.assertSoftly {
            tittel shouldBe dokumenttype.tittel
            dokumenter.shouldForExactly(1) { dokument ->
                dokument.tittel shouldBe dokumenttype.dokumenttittel
            }
            eksternReferanseId shouldBe "1BARNEBRILLEAPI"
            journalfoerendeEnhet shouldBe "9999"
        }
        forsøkFerdigstillSlot shouldHaveCaptured true
    }
}
