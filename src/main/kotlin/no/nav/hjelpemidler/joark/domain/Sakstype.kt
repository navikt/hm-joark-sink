package no.nav.hjelpemidler.joark.domain

enum class Sakstype(val dokumenttype: Dokumenttype) {
    SØKNAD(Dokumenttype.SØKNAD_OM_HJELPEMIDLER),
    BESTILLING(Dokumenttype.BESTILLING_AV_TEKNISKE_HJELPEMIDLER),
    DELBESTILLING(Dokumenttype.BESTILLING_AV_DELER_TIL_TEKNISKE_HJELPEMIDLER), // TODO: Høre med Trygve om hva som er riktig brevkode for delbestilling
    BYTTE(Dokumenttype.BYTTE_AV_HJELPEMIDLER),
    BRUKERPASSBYTTE(Dokumenttype.BRUKERPASSBYTTE_AV_HJELPEMIDLER),
    BARNEBRILLER(Dokumenttype.TILSKUDD_VED_KJØP_AV_BRILLER_TIL_BARN),
    ;
}
