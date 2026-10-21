package no.nav.hjelpemidler.joark.dokarkiv

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.hjelpemidler.domain.organisasjon.Organisasjonsnummer
import no.nav.hjelpemidler.domain.person.AktørId
import no.nav.hjelpemidler.domain.person.Fødselsnummer
import no.nav.hjelpemidler.domain.person.år
import no.nav.hjelpemidler.joark.sink.dokarkiv.avsenderMottakerMedPartId
import no.nav.hjelpemidler.joark.sink.dokarkiv.models.AvsenderMottaker
import kotlin.test.Test

class AvsenderMottakerTest {
    @Test
    fun `fødselsnummer gir idType FNR`() {
        val fnr = Fødselsnummer(40.år)
        avsenderMottakerMedPartId(fnr, "Ola") shouldBe AvsenderMottaker(fnr.value, AvsenderMottaker.IdType.FNR, "Ola")
    }

    @Test
    fun `organisasjonsnummer gir idType ORGNR`() {
        val orgnr = Organisasjonsnummer("889640782")
        avsenderMottakerMedPartId(orgnr) shouldBe AvsenderMottaker(orgnr.value, AvsenderMottaker.IdType.ORGNR, null)
    }

    @Test
    fun `id, idType og navn kan alle være null`() {
        avsenderMottakerMedPartId(null) shouldBe AvsenderMottaker(null, null, null)
        avsenderMottakerMedPartId(null, "Ola") shouldBe AvsenderMottaker(null, null, "Ola")
    }

    @Test
    fun `ukjent id-type feiler`() {
        shouldThrow<IllegalStateException> {
            avsenderMottakerMedPartId(AktørId("1234567890123"))
        }
    }
}
