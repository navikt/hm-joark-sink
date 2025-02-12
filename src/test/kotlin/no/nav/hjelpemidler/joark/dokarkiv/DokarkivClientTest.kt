package no.nav.hjelpemidler.joark.dokarkiv

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.joark.dokarkiv.models.FerdigstillJournalpostRequest
import no.nav.hjelpemidler.joark.dokarkiv.models.KnyttTilAnnenSakRequest
import no.nav.hjelpemidler.joark.dokarkiv.models.KnyttTilAnnenSakResponse
import no.nav.hjelpemidler.joark.dokarkiv.models.OppdaterJournalpostRequest
import no.nav.hjelpemidler.joark.dokarkiv.models.OppdaterJournalpostResponse
import no.nav.hjelpemidler.joark.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.joark.dokarkiv.models.OpprettJournalpostResponse
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.test.TestOpenIDClient
import no.nav.hjelpemidler.joark.test.respondJson
import kotlin.test.Test

class DokarkivClientTest {
    private val tokenSetProvider = TestOpenIDClient().withScope("test")

    @Test
    fun `oppretter journalpost`() = runTest {
        val client = DokarkivClient(tokenSetProvider, MockEngine { request ->
            request.url.fullPath shouldBe "/rest/journalpostapi/v1/journalpost?forsoekFerdigstill=true"
            respondJson(
                OpprettJournalpostResponse(
                    journalpostId = "1", dokumenter = emptyList(), journalpostferdigstilt = true
                ), HttpStatusCode.Created
            )
        })

        val lagRequest = OpprettJournalpostRequestConfigurer(
            "12345678910",
            "12345678910",
            Dokumenttype.INNHENTE_OPPLYSNINGER_BARNEBRILLER,
            OpprettJournalpostRequest.Journalposttype.UTGAAENDE,
            "test",
        ).apply {
            dokument(fysiskDokument = ByteArray(0))
        }

        client.opprettJournalpost(lagRequest(), forsøkFerdigstill = true)
    }

    @Test
    fun `oppdaterer journalpost`() = runTest {
        val client = DokarkivClient(tokenSetProvider, MockEngine { request ->
            request.url.fullPath shouldBe "/rest/journalpostapi/v1/journalpost/1"
            respondJson(
                OppdaterJournalpostResponse(journalpostId = "1"), HttpStatusCode.OK
            )
        })

        client.oppdaterJournalpost("1", OppdaterJournalpostRequest())
    }

    @Test
    fun `ferdigstiller journalpost`() = runTest {
        val client = DokarkivClient(tokenSetProvider, MockEngine { request ->
            request.url.fullPath shouldBe "/rest/journalpostapi/v1/journalpost/1/ferdigstill"
            respondOk()
        })

        client.ferdigstillJournalpost("1", FerdigstillJournalpostRequest(""))
    }

    @Test
    fun `feilregistrerer sakstilknytning`() = runTest {
        val client = DokarkivClient(tokenSetProvider, MockEngine { request ->
            request.url.fullPath shouldBe "/rest/journalpostapi/v1/journalpost/1/feilregistrer/feilregistrerSakstilknytning"
            respondOk()
        })

        client.feilregistrerSakstilknytning("1")
    }

    @Test
    fun `knytter til annen sak`() = runTest {
        val client = DokarkivClient(tokenSetProvider, MockEngine { request ->
            request.url.fullPath shouldBe "/rest/journalpostapi/v1/journalpost/1/knyttTilAnnenSak"
            respondJson(KnyttTilAnnenSakResponse())
        })

        client.knyttTilAnnenSak("1", KnyttTilAnnenSakRequest())
    }
}
