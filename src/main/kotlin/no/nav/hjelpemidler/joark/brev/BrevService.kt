package no.nav.hjelpemidler.joark.brev

import io.ktor.client.call.body
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

class BrevService(val brevClient: BrevClient) {
    suspend fun lagAvvisningsBrev(flettefelter: FlettefelterAvvisning, begrunnelser: List<String>, målform: Målform = Målform.BOKMÅL, viseNavAdresse: Boolean = true): ByteArray {
        log.info("Lager avvisningsbrev for optiker")
        val reqData = HentAvvisningsbrevRequest(
            flettefelter = flettefelter,
            begrunnelser = begrunnelser, // Felt: Årsaker
            betingelser = Betingelser(viseNavAdresse = viseNavAdresse),
        )

        val req = HentBrevRequest(
            Brevmal.BARNEBRILLER_VEDTAK_OPTIKER_AVVISNING,
            målform,
            reqData,
        )

        return brevClient.lagBrev(req).body<ByteArray>()
    }
}

enum class Brevmal(
    val apiNavn: String,
) {
    BARNEBRILLER_VEDTAK_OPTIKER_AVVISNING(
        apiNavn = "barnebrillerAvvisningDirekteoppgjor",
    ),
}

enum class Språkkode {
    NB, NN,
}

enum class Målform(val språkkode: Språkkode, val urlNavn: String) {
    BOKMÅL(språkkode = Språkkode.NB, urlNavn = "bokmaal"),
    NYNORSK(språkkode = Språkkode.NN, urlNavn = "nynorsk"),
}

data class FlettefelterAvvisning(
    val brevOpprettetDato: String,
    val barnetsFulleNavn: String,
    val barnetsFodselsnummer: String,
    val mottattDato: String,
    val bestillingsDato: String,
    val optikerForretning: String,
    val sfæriskStyrkeHøyre: String,
    val sfæriskStyrkeVenstre: String,
    val cylinderstyrkeHøyre: String,
    val cylinderstyrkeVenstre: String,
    val forrigeBrilleDato: String,
)

data class Betingelser(
    val viseNavAdresse: Boolean,
)

data class HentAvvisningsbrevRequest(
    val flettefelter: FlettefelterAvvisning,
    val begrunnelser: List<String>,
    val betingelser: Betingelser,
) : Data

sealed interface Data
