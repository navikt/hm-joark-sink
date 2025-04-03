package no.nav.hjelpemidler.joark.service.hotsak

import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.slot
import no.nav.hjelpemidler.domain.person.Fødselsnummer
import no.nav.hjelpemidler.domain.person.år
import no.nav.hjelpemidler.joark.dokarkiv.models.JournalpostOpprettet
import no.nav.hjelpemidler.joark.service.hotsak.SaksnotatOpprettetOpprettOgFerdigstillJournalpost.SaksnotatOpprettetMessage
import no.nav.hjelpemidler.joark.test.AbstractListenerTest
import no.nav.hjelpemidler.joark.test.assertSoftly
import no.nav.hjelpemidler.joark.test.shouldHaveCaptured
import no.nav.hjelpemidler.rapids_and_rivers.register
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class SaksnotatOpprettetOpprettOgFerdigstillJournalpostTest : AbstractListenerTest() {
    private val opprettetAvSlot = slot<String>()

    private val fnrBruker = Fødselsnummer(40.år)

    @BeforeTest
    fun setUp() {
        configure { connection, journalpostService ->
            connection.register(SaksnotatOpprettetOpprettOgFerdigstillJournalpost(journalpostService))
        }
    }

    @Test
    fun `Skal opprette journalpost for saksnotat`() {
        coEvery {
            dokarkivClientMock.opprettJournalpost(
                capture(opprettJournalpostRequestSlot),
                capture(forsøkFerdigstillSlot),
                capture(opprettetAvSlot),
            )
        } returns JournalpostOpprettet("1", setOf("2"), true)

        sendTestMessage(
            "sakId" to "1000",
            "saksnotatId" to "1050",
            "fnrBruker" to fnrBruker,
            "dokumenttittel" to "Saksnotat",
            "fysiskDokument" to pdf,
            "strukturertDokument" to mapOf<String, Any?>(),
            "opprettetAv" to "X999999",
            "eventId" to UUID.randomUUID(),
            "eventName" to SaksnotatOpprettetMessage.EVENT_NAME,
        )

        opprettJournalpostRequestSlot.assertSoftly {
            it.sak?.fagsakId shouldBe "1000"
            it.dokumenter.shouldBeSingleton { dokument ->
                dokument.tittel shouldBe "Saksnotat"
            }
            it.bruker?.id shouldBe fnrBruker.toString()
            it.kanal shouldBe null
        }
        opprettetAvSlot shouldHaveCaptured "X999999"
    }
}
