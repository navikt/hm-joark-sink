package no.nav.hjelpemidler.domain

import no.nav.hjelpemidler.domain.Dokumenttype

enum class Sakstype(val dokumenttype: Dokumenttype) {
    SØKNAD(Dokumenttype.SØKNAD_OM_HJELPEMIDLER),
    BESTILLING(Dokumenttype.BESTILLING_AV_TEKNISKE_HJELPEMIDLER),
    BARNEBRILLER(Dokumenttype.TILSKUDD_VED_KJØP_AV_BRILLER_TIL_BARN),
    ;
}
