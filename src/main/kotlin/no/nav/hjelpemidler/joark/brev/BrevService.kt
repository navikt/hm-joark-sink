package no.nav.hjelpemidler.joark.brev

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.body

private val log = KotlinLogging.logger {}

class BrevService(val brevClient: BrevClient) {
    suspend fun lagStansetbrev(
        flettefelter: FlettefelterAvvisning,
        begrunnelser: List<String>,
        målform: Målform = Målform.BOKMÅL,
    ): ByteArray {
        log.info { "Lager stansetbrev for optiker" }
        val reqData = HentAvvisningsbrevRequest(
            flettefelter = flettefelter,
            begrunnelser = begrunnelser,
            betingelser = Betingelser(viseNavAdresse = true, viseNavAdresseHoT = false),
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
    val viseNavAdresseHoT: Boolean,
)

data class HentAvvisningsbrevRequest(
    val flettefelter: FlettefelterAvvisning,
    val begrunnelser: List<String>,
    val betingelser: Betingelser,
) : Data

sealed interface Data
