package no.nav.hjelpemidler.joark.domain

enum class Sakstype(val dokumenttype: Dokumenttype) {
    SØKNAD(Dokumenttype.SØKNAD_OM_HJELPEMIDLER),
    BESTILLING(Dokumenttype.BESTILLING_AV_TEKNISKE_HJELPEMIDLER),
    BYTTE(Dokumenttype.BYTTE_AV_HJELPEMIDLER),
    BRUKERPASS_BYTTE(Dokumenttype.BRUKERPASSBYTTE_AV_HJELPEMIDLER),
    BARNEBRILLER(Dokumenttype.TILSKUDD_VED_KJØP_AV_BRILLER_TIL_BARN),
    ;
}
