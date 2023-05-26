package no.nav.hjelpemidler.joark.service

enum class Dokumenttype(
    val brevkode: String?,
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
    TILSKUDD_VED_KJØP_AV_BRILLER_TIL_BARN(
        brevkode = "NAV 10-07.34",
        tittel = "Tilskudd ved kjøp av briller til barn"
    ),
    TILSKUDD_VED_KJØP_AV_BRILLER_TIL_BARN_ETTERSENDING(
        brevkode = "NAVe 10-07.34",
        tittel = "Tilskudd ved kjøp av briller til barn - Ettersending"
    ),
    VEDTAKSBREV_BARNEBRILLER_OPTIKER(
        brevkode = "vedtaksbrev_barnebriller_optiker",
        tittel = "Vedtaksbrev barnebriller",
        dokumenttittel = "Tilskudd ved kjøp av briller til barn via optiker"
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
    VEDTAKSBREV_BARNEBRILLER_HOTSAK_INNVILGET(
        brevkode = "vedtaksbrev_barnebriller_hotsak_innvilget",
        tittel = "Innvilget: Vedtaksbrev barnebriller",
        dokumenttittel = "Innvilget: Tilskudd ved kjøp av briller til barn",
    ),
    ;
}
