package no.nav.hjelpemidler.joark.service

enum class Sakstype(val dokumenttype: Dokumenttype? = null) {
    SØKNAD(Dokumenttype.SØKNAD_OM_HJELPEMIDLER),
    BESTILLING(Dokumenttype.BESTILLING_AV_TEKNISKE_HJELPEMIDLER),
    BARNEBRILLER,
    ;
}
