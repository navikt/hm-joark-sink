package no.nav.hjelpemidler.joark.service.barnebriller

import io.kotest.inspectors.shouldForExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.slot
import no.nav.hjelpemidler.domain.person.Fødselsnummer
import no.nav.hjelpemidler.domain.person.år
import no.nav.hjelpemidler.joark.sink.domain.Dokumenttype
import no.nav.hjelpemidler.joark.sink.domain.JournalpostOpprettet
import no.nav.hjelpemidler.joark.sink.service.barnebriller.OpprettOgFerdigstillJournalpostBarnebriller
import no.nav.hjelpemidler.joark.sink.service.barnebriller.ResendJournalpostBarnebriller
import no.nav.hjelpemidler.joark.test.AbstractListenerTest
import no.nav.hjelpemidler.joark.test.assertSoftly
import no.nav.hjelpemidler.joark.test.shouldHaveCaptured
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class JournalpostBarnebrillerTest :
    AbstractListenerTest({ rapid, service ->
        OpprettOgFerdigstillJournalpostBarnebriller(rapid, service)
        ResendJournalpostBarnebriller(rapid, service)
    }) {
    private val pdfData = slot<String>()

    @BeforeTest
    fun setUp() {
        coEvery {
            pdfGeneratorClientMock.genererPdfBarnebriller(capture(pdfData))
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
        verifyRequest("hm-barnebrillevedtak-opprettet", "1BARNEBRILLEAPI")
    }

    @Test
    fun `Skal generere samme PDF-format ved rejournalføring`() {
        verifyRequest("hm-barnebrillevedtak-rekjor", "RE_1BARNEBRILLEAPI")
    }

    private fun verifyRequest(eventName: String, expectedReference: String) {
        val fnr = Fødselsnummer(10.år).value
        sendTestMessage(
            "eventName" to eventName,
            "fnr" to fnr,
            "brukersNavn" to "test",
            "orgnr" to "123456789",
            "orgNavn" to "test",
            "orgAdresse" to "test",
            "navnAvsender" to "test",
            "eventId" to UUID.randomUUID(),
            "opprettetDato" to LocalDateTime.parse("2026-11-24T10:15:30"),
            "sakId" to "1",
            "brilleseddel" to jsonMapper.createObjectNode(),
            "bestillingsdato" to "2023-05-01",
            "bestillingsreferanse" to "test",
            "satsBeskrivelse" to "test",
            "satsBeløp" to "2475",
            "beløp" to "1200.75",
        )
        val dokumenttype = Dokumenttype.KRAV_BARNEBRILLER_OPTIKER
        opprettJournalpostRequestSlot.assertSoftly {
            tittel shouldBe dokumenttype.tittel
            dokumenter.shouldForExactly(1) { dokument ->
                dokument.tittel shouldBe dokumenttype.dokumenttittel
            }
            eksternReferanseId shouldBe expectedReference
            journalfoerendeEnhet shouldBe "9999"
        }
        forsøkFerdigstillSlot shouldHaveCaptured true
        jsonMapper.readTree(pdfData.captured).apply {
            get("fnr").asText() shouldBe fnr
            get("brilleseddel").isObject shouldBe true
            get("brilleseddel").isEmpty shouldBe true
            get("beløp").decimalValue().toPlainString() shouldBe "1200.75"
            get("bestillingsdato").asText() shouldBe "2023-05-01"
            get("bestillingsår").asInt() shouldBe 2023
            get("opprettet").asText() shouldBe "2026-11-24T10:15:30"
            has("opprettetDato") shouldBe false
        }
    }
}
