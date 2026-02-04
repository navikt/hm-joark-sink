package no.nav.hjelpemidler.joark.domain

enum class Dokumenttype(
    val brevkode: String,
    val tittel: String,
    val dokumenttittel: String = tittel,
) {
    SØKNAD_OM_HJELPEMIDLER(
        brevkode = "NAV 10-07.03",
        tittel = "Søknad om hjelpemidler"
    ),
    BESTILLING_AV_TEKNISKE_HJELPEMIDLER(
        brevkode = "NAV 10-07.05",
        tittel = "Bestilling av tekniske hjelpemidler"
    ),
    BYTTE_AV_HJELPEMIDLER(
        brevkode = "NAV 10-07.31",
        tittel = "Bytte av hjelpemiddel"
    ),
    BRUKERPASSBYTTE_AV_HJELPEMIDLER(
        brevkode = "NAV 10-07.31",
        tittel = "Brukerpassbytte av hjelpemiddel"
    ),
    TILSKUDD_VED_KJØP_AV_BRILLER_TIL_BARN(
        brevkode = "NAV 10-07.34",
        tittel = "Tilskudd ved kjøp av briller til barn"
    ),
    TILSKUDD_VED_KJØP_AV_BRILLER_TIL_BARN_ETTERSENDELSE(
        brevkode = "NAVe 10-07.34",
        tittel = "Ettersendelse til tilskudd ved kjøp av briller til barn",
    ),
    KRAV_BARNEBRILLER_OPTIKER(
        brevkode = "krav_barnebriller_optiker",
        tittel = "Tilskudd ved kjøp av briller til barn via optiker",
        dokumenttittel = "Tilskudd ved kjøp av briller til barn via optiker"
    ),
    KRAV_BARNEBRILLER_OPTIKER_AVVISNING(
        brevkode = "krav_barnebriller_optiker_avvisning",
        tittel = "Stanset behandling av tilskudd til kjøp av briller til barn via optiker",
        dokumenttittel = "Stanset behandling av tilskudd til kjøp av briller til barn via optiker",
    ),
    VEDTAKSBREV_BARNEBRILLER_HOTSAK(
        brevkode = "vedtaksbrev_barnebriller_hotsak",
        tittel = "Vedtaksbrev barnebriller",
        dokumenttittel = "Tilskudd ved kjøp av briller til barn",
    ),
    VEDTAKSBREV_BARNEBRILLER_HOTSAK_AVSLAG(
        brevkode = "vedtaksbrev_barnebriller_hotsak_avslag",
        tittel = "Avslag: Vedtaksbrev barnebriller",
        dokumenttittel = "Avslag: Tilskudd ved kjøp av briller til barn",
    ),
    VEDTAKSBREV_BARNEBRILLER_HOTSAK_INNVILGELSE(
        brevkode = "vedtaksbrev_barnebriller_hotsak_innvilgelse",
        tittel = "Innvilgelse: Vedtaksbrev barnebriller",
        dokumenttittel = "Innvilgelse: Tilskudd ved kjøp av briller til barn",
    ),
    INNHENTE_OPPLYSNINGER_BARNEBRILLER(
        brevkode = "innhente_opplysninger_barnebriller",
        tittel = "Briller til barn: Nav etterspør opplysninger",
        dokumenttittel = "Briller til barn: Nav etterspør opplysninger",
    ),
    NOTAT(
        brevkode = "HJE_NOT_001",
        tittel = "Journalført notat i sak",
        dokumenttittel = "Journalført notat i sak",
    ),
    BREVEDITOR_VEDTAKSBREV(
        brevkode = "vedtaksbrev_hotsak_breveditor",
        tittel = "Vedtak for søknad om hjelpemidler",
        dokumenttittel = "Vedtak for søknad om hjelpemidler",
    ),
    ;
}

val brevkodeForEttersendelse: Map<Dokumenttype, String> = mapOf(
    Dokumenttype.INNHENTE_OPPLYSNINGER_BARNEBRILLER to Dokumenttype.TILSKUDD_VED_KJØP_AV_BRILLER_TIL_BARN.brevkode,
)
