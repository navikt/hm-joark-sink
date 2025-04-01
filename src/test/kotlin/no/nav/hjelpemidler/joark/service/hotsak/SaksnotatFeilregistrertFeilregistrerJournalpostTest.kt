package no.nav.hjelpemidler.joark.service.hotsak

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.throwable.shouldHaveMessage
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.verify
import no.nav.hjelpemidler.joark.test.AbstractListenerTest
import java.util.UUID
import kotlin.test.Test

class SaksnotatFeilregistrertFeilregistrerJournalpostTest :
    AbstractListenerTest(::SaksnotatFeilregistrertFeilregistrerJournalpost) {
    private val journalpostId = "204080"

    @Test
    fun `Feilregistrerer sakstilknytning i Joark hvis melding validerer`() {
        coEvery { dokarkivClientMock.feilregistrerSakstilknytning(journalpostId) } returns Unit

        sendTestMessage(
            "sakId" to "1000",
            "saksnotatId" to "1050",
            "journalpostId" to journalpostId,
            "eventId" to UUID.randomUUID(),
            "eventName" to SaksnotatFeilregistrertMessage.EVENT_NAME,
        )

        coVerify(exactly = 1) { dokarkivClientMock.feilregistrerSakstilknytning(journalpostId) }
    }

    @Test
    fun `Listener feiler hvis melding ikke validerer`() {
        shouldThrow<IllegalStateException> {
            sendTestMessage(
                "sakId" to "1000",
                // saksnotatId mangler
                "journalpostId" to journalpostId,
                "eventId" to UUID.randomUUID(),
                "eventName" to SaksnotatFeilregistrertMessage.EVENT_NAME,
            )
        } shouldHaveMessage "Validering av melding feilet, se secureLog for detaljer"

        verify { dokarkivClientMock wasNot Called }
    }
}
