package no.nav.hjelpemidler.joark.service.barnebriller

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.coEvery
import io.mockk.coVerify
import no.nav.hjelpemidler.joark.test.TestSupport
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class FeilregistrerJournalpostBarnebrillerTest : TestSupport() {
    override fun TestRapid.configure() {
        FeilregistrerJournalpostBarnebriller(this, journalpostService)
    }

    @BeforeTest
    fun setUp() {
        coEvery {
            dokarkivClientMock.feilregistrerSakstilknytning(any())
        } returns Unit
    }

    @Test
    fun `Skal feilregistrere journalpost for barnebriller`() {
        val journalpostId = "2"
        sendTestMessage(
            "eventId" to UUID.randomUUID(),
            "eventName" to "hm-barnebriller-feilregistrer-journalpost",
            "sakId" to "1",
            "joarkRef" to journalpostId,
            "opprettet" to LocalDateTime.now()
        )
        coVerify {
            dokarkivClientMock.feilregistrerSakstilknytning(journalpostId)
        }
    }
}
