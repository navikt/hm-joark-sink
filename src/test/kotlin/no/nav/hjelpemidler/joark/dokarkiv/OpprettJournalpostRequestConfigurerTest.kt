package no.nav.hjelpemidler.joark.dokarkiv

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.nav.hjelpemidler.domain.person.Fødselsnummer
import no.nav.hjelpemidler.domain.person.år
import no.nav.hjelpemidler.joark.sink.dokarkiv.OpprettJournalpostRequestConfigurer
import no.nav.hjelpemidler.joark.sink.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.joark.sink.domain.Dokumenttype
import kotlin.test.Test

class OpprettJournalpostRequestConfigurerTest {
    private fun configurer(
        journalposttype: OpprettJournalpostRequest.Journalposttype,
        navnMottaker: String? = null,
    ) = OpprettJournalpostRequestConfigurer(
        fnrBruker = Fødselsnummer(40.år).value,
        partIdMottaker = null,
        dokumenttype = Dokumenttype.INNHENTE_OPPLYSNINGER_BARNEBRILLER,
        journalposttype = journalposttype,
        eksternReferanseId = "test",
        navnMottaker = navnMottaker,
    ).apply {
        dokument(fysiskDokument = ByteArray(0))
    }

    @Test
    fun `utgående journalpost uten id og navn på mottaker feiler`() {
        shouldThrow<IllegalStateException> {
            configurer(OpprettJournalpostRequest.Journalposttype.UTGAAENDE)()
        }
        shouldThrow<IllegalStateException> {
            configurer(OpprettJournalpostRequest.Journalposttype.UTGAAENDE, navnMottaker = " ")()
        }
    }

    @Test
    fun `utgående journalpost med kun navn på mottaker er gyldig`() {
        val avsenderMottaker = configurer(
            OpprettJournalpostRequest.Journalposttype.UTGAAENDE,
            navnMottaker = "Ola Nordmann",
        )().avsenderMottaker

        avsenderMottaker?.navn shouldBe "Ola Nordmann"
        avsenderMottaker?.id.shouldBeNull()
        avsenderMottaker?.idType.shouldBeNull()
    }

    @Test
    fun `notat uten mottaker er gyldig`() {
        configurer(OpprettJournalpostRequest.Journalposttype.NOTAT)().avsenderMottaker.shouldBeNull()
    }
}
