package no.nav.hjelpemidler.joark.service.hotsak

import io.kotest.inspectors.shouldForExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import no.nav.hjelpemidler.joark.dokarkiv.models.JournalpostOpprettet
import no.nav.hjelpemidler.joark.dokarkiv.models.Sak
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.test.AbstractListenerTest
import no.nav.hjelpemidler.joark.test.assertSoftly
import no.nav.hjelpemidler.joark.test.shouldHaveCaptured
import java.time.LocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test

class VedtakBarnebrillerOpprettOgFerdigstillJournalpostTest :
    AbstractListenerTest(::VedtakBarnebrillerOpprettOgFerdigstillJournalpost) {
    @BeforeTest
    fun setUp() {
        coEvery {
            dokarkivClientMock.opprettJournalpost(
                capture(opprettJournalpostRequestSlot),
                capture(forsøkFerdigstillSlot)
            )
        } returns JournalpostOpprettet("1", setOf("1"), true)
    }

    @Test
    fun `Sender vedtak om innvilget brillestøtte til barn til journalføring`() {
        val sakId = "1"
        sendTestMessage(
            "eventName" to "hm-manuelt-barnebrillevedtak-opprettet",
            "saksnummer" to sakId,
            "fnrBruker" to "10101012345",
            "navnBruker" to "test",
            "navnAvsender" to "test",
            "opprettet" to LocalDateTime.now(),
            "pdf" to pdf,
            "vedtaksstatus" to Vedtaksstatus.INNVILGET,
        )
        val dokumenttype = Dokumenttype.VEDTAKSBREV_BARNEBRILLER_HOTSAK_INNVILGELSE
        opprettJournalpostRequestSlot.assertSoftly {
            tittel shouldBe dokumenttype.tittel
            dokumenter.shouldForExactly(1) { dokument ->
                dokument.tittel shouldBe dokumenttype.dokumenttittel
            }
            sak shouldBe Sak(sakId, Sak.Fagsaksystem.HJELPEMIDLER, Sak.Sakstype.FAGSAK)
            eksternReferanseId shouldBe "${sakId}BARNEBRILLEVEDTAK"
            journalfoerendeEnhet shouldBe "9999"
        }
        forsøkFerdigstillSlot shouldHaveCaptured true
    }
}
