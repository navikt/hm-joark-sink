package no.nav.hjelpemidler.joark.brev

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.joark.pdf.PdfGeneratorClient
import java.time.LocalDate

private val log = KotlinLogging.logger {}

class BrevService(val pdfGeneratorClient: PdfGeneratorClient) {
    suspend fun lagStansetbrev(
        data: BarnebrillerAvvisningDirekteoppgjor,
        målform: Målform = Målform.BOKMÅL,
    ): ByteArray {
        log.info { "Lager stansetbrev for optiker" }
        return pdfGeneratorClient.lagBrev("brille-api", Brevmal.BARNEBRILLER_VEDTAK_OPTIKER_AVVISNING.apiNavn, målform, data)
    }
}

enum class Brevmal(
    val apiNavn: String,
) {
    BARNEBRILLER_VEDTAK_OPTIKER_AVVISNING(
        apiNavn = "barnebrillerAvvisningDirekteoppgjor",
    ),
}

enum class Målform {
    BOKMÅL,
    NYNORSK,
}

data class BarnebrillerAvvisningDirekteoppgjorBegrunnelser (
    val stansetEksisterendeVedtak: Boolean? = false,
    val stansetOver18: Boolean? = false,
    val stansetIkkeMedlem: Boolean? = false,
    val stansetForLavBrillestyrke: Boolean? = false,
    val stansetBestillingsdatoEldreEnn6Mnd: Boolean? = false,
)

data class BarnebrillerAvvisningDirekteoppgjor(
    val viseNavAdresse: Boolean,
    val mottattDato: LocalDate? = null,
    val brevOpprettetDato: LocalDate,
    val bestillingsDato: LocalDate? = null,
    val forrigeBrilleDato: LocalDate? = null,
    val optikerVirksomhetNavn: String,
    val optikerVirksomhetOrgnr: String,
    val barnetsFulleNavn: String,
    val barnetsFodselsnummer: String,
    val sfæriskStyrkeHøyre: String? = null,
    val cylinderstyrkeHøyre: String? = null,
    val sfæriskStyrkeVenstre: String? = null,
    val cylinderstyrkeVenstre: String? = null,
    val begrunnelser: BarnebrillerAvvisningDirekteoppgjorBegrunnelser,
)
