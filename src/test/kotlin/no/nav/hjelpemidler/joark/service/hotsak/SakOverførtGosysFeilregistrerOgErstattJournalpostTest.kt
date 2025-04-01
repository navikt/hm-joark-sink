package no.nav.hjelpemidler.joark.service.hotsak

import io.mockk.coEvery
import io.mockk.coVerify
import no.nav.hjelpemidler.joark.domain.Sakstype
import no.nav.hjelpemidler.joark.test.AbstractListenerTest
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class SakOverførtGosysFeilregistrerOgErstattJournalpostTest :
    AbstractListenerTest(::SakOverførtGosysFeilregistrerOgErstattJournalpost) {
    private val nå = LocalDateTime.now()
    private val fnrBruker = "10101012345"
    private val journalpostId = "2"

    @BeforeTest
    fun setUp() {
        coEvery {
            dokarkivClientMock.feilregistrerSakstilknytning(journalpostId)
        } returns Unit
        coEvery {
            dokarkivClientMock.kopierJournalpost(journalpostId, any())
        } returns "3"
    }

    @Test
    fun `Kopierer hvis sakstype er SØKNAD`() {
        sendTestMessage(sakstype = Sakstype.SØKNAD)

        coVerify(exactly = 1) { dokarkivClientMock.kopierJournalpost(journalpostId, any()) }
    }

    @Test
    fun `Kopierer journalpost hvis sakstype er BESTILLING`() {
        sendTestMessage(sakstype = Sakstype.BESTILLING)

        coVerify(exactly = 1) { dokarkivClientMock.kopierJournalpost(journalpostId, any()) }
    }

    @Test
    fun `Kopierer journalpost hvis sakstype er BARNEBRILLER`() {
        sendTestMessage(sakstype = Sakstype.BARNEBRILLER)

        coVerify(exactly = 1) { dokarkivClientMock.kopierJournalpost(journalpostId, any()) }
    }

    private fun sendTestMessage(sakstype: Sakstype) =
        sendTestMessage(
            "eventName" to "hm-sakTilbakeførtGosys",
            "saksnummer" to "1",
            "joarkRef" to journalpostId,
            "navnBruker" to "test",
            "fnrBruker" to fnrBruker,
            "dokumentBeskrivelse" to "test",
            "soknadId" to UUID.randomUUID(),
            "mottattDato" to nå,
            "sakstype" to sakstype,
            "navIdent" to "X123456",
            "valgteÅrsaker" to jsonMapper.createArrayNode(),
            "enhet" to "1000",
            "soknadJson" to jsonMapper.createObjectNode(),
        )
}
