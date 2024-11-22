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

    @Test
    fun some() {
        val input = """
            {
                "saksnummer": "1234",
                "joarkRef": "journalpost01234",
                "fnrBruker": "15084300133",
                "dokumentBeskrivelse": "docdesc",
                "soknadId": "b4016401-9f6e-4b25-bb55-84dae6bff9d4",
                "sakstype": "SØKNAD",
                "navIdent": "IT010203",
                "valgteÅrsaker": ["ÅRSAK1", "ÅRSAK2"],
                "enhet": "enhet01",
                "begrunnelse": "Min begrunnelse",
                "soknadJson": {},
                "prioritet": "HØY"
            }
        """.trimIndent()

        // val parsed = jsonMapper.readValue<MottattJournalpostData>(input)
        // println(jsonMapper.writeValueAsString(parsed))
    }
}
