package no.nav.hjelpemidler.joark.sink.pdf

import no.nav.hjelpemidler.domain.kodeverk.NavSkjema
import no.nav.hjelpemidler.joark.sink.domain.Språkkode
import no.nav.hjelpemidler.joark.sink.førstesidegenerator.models.Adresse
import no.nav.hjelpemidler.joark.sink.førstesidegenerator.models.Bruker
import no.nav.hjelpemidler.joark.sink.førstesidegenerator.models.PostFoerstesideRequest
import no.nav.hjelpemidler.joark.sink.førstesidegenerator.models.PostFoerstesideRequest.Foerstesidetype
import no.nav.hjelpemidler.joark.sink.saf.enums.Tema

class OpprettFørstesideRequestConfigurer(
    val tittel: String,
    val fnrBruker: String,
) {
    var språkkode: Språkkode = Språkkode.NB
    var adresse: Adresse = defaultAdresse
    var navSkjema: NavSkjema? = null
    var arkivtittel: String? = when (navSkjema) {
        NavSkjema.NAV_10_07_34 -> "Briller til barn: Ettersendelse"
        else -> "Søknad om hjelpemidler: Ettersendelse"
    }
    var enhetsnummer: String? = null

    operator fun invoke(): PostFoerstesideRequest = OpprettFørstesideRequest(
        spraakkode = språkkode.førstesidegenerator,
        overskriftstittel = tittel,
        foerstesidetype = Foerstesidetype.ETTERSENDELSE,
        adresse = adresse,
        netsPostboks = null,
        avsender = null,
        bruker = Bruker(fnrBruker, Bruker.BrukerType.PERSON),
        tema = Tema.HJE.toString(),
        behandlingstema = null,
        arkivtittel = arkivtittel,
        vedleggsliste = vedlegg[språkkode],
        navSkjemaId = navSkjema?.kode,
        dokumentlisteFoersteside = vedlegg[språkkode],
        enhetsnummer = enhetsnummer,
    )
}

val vedlegg = mapOf(
    Språkkode.NB to listOf("Se vedlagte brev"),
    Språkkode.NN to listOf("Sjå vedlagte brev"),
    Språkkode.EN to listOf("See the attached letters"),
)

val defaultAdresse = Adresse(
    adresselinje1 = "Nav Skanning",
    adresselinje2 = "Postboks 1400",
    postnummer = "0109",
    poststed = "OSLO",
)
