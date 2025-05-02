package no.nav.hjelpemidler.joark.pdf

import no.nav.hjelpemidler.joark.domain.Språkkode
import no.nav.hjelpemidler.joark.førstesidegenerator.models.Adresse
import no.nav.hjelpemidler.joark.førstesidegenerator.models.Bruker
import no.nav.hjelpemidler.joark.førstesidegenerator.models.PostFoerstesideRequest
import no.nav.hjelpemidler.joark.førstesidegenerator.models.PostFoerstesideRequest.Foerstesidetype
import no.nav.hjelpemidler.saf.enums.Tema

class OpprettFørstesideRequestConfigurer(
    val tittel: String,
    val fnrBruker: String,
) {
    var språkkode: Språkkode = Språkkode.NB
    var adresse: Adresse = defaultAdresse
    var brevkode: String? = null
    var arkivtittel: String? = "Briller til barn: Ettersendelse" // TODO Midlertidig hardkodet til problemene i hm-saksbehandling er løst. På grunn av litt ustabil oppførsel, er det litt risikabelt å prodsette nye endringer på hm-saksbehandling. Når det er løst, kan arkivtittel leses fra et eget felt i kafka meldingen
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
        navSkjemaId = brevkode,
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
