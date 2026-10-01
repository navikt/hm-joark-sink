package no.nav.hjelpemidler.joark.sink.domain

import no.nav.hjelpemidler.domain.kodeverk.Brevkode
import no.nav.hjelpemidler.domain.kodeverk.NavSkjema

enum class Dokumenttype(
    override val kode: String,
    val dokumenttittel: String,
    /**
     * Tittel som beskriver journalposten samlet.
     */
    val tittel: String = dokumenttittel,
) : Brevkode {
    SØKNAD_OM_HJELPEMIDLER(skjema = NavSkjema.NAV_10_07_03),
    BESTILLING_AV_TEKNISKE_HJELPEMIDLER(skjema = NavSkjema.NAV_10_07_05),
    BYTTE_AV_HJELPEMIDLER(skjema = NavSkjema.NAV_10_07_31),
    BRUKERPASSBYTTE_AV_HJELPEMIDLER(
        skjema = NavSkjema.NAV_10_07_31,
        dokumenttittel = "Brukerpassbytte av hjelpemiddel",
    ),
    TILSKUDD_VED_KJØP_AV_BRILLER_TIL_BARN(skjema = NavSkjema.NAV_10_07_34),
    TILSKUDD_VED_KJØP_AV_BRILLER_TIL_BARN_ETTERSENDELSE(skjema = NavSkjema.NAVe_10_07_34),
    KRAV_BARNEBRILLER_OPTIKER(
        kode = "krav_barnebriller_optiker",
        dokumenttittel = "Tilskudd ved kjøp av briller til barn via optiker",
    ),
    KRAV_BARNEBRILLER_OPTIKER_AVVISNING(
        kode = "krav_barnebriller_optiker_avvisning",
        dokumenttittel = "Stanset behandling av tilskudd til kjøp av briller til barn via optiker",
    ),
    VEDTAKSBREV_BARNEBRILLER_HOTSAK_AVSLAG(
        kode = "vedtaksbrev_barnebriller_hotsak_avslag",
        dokumenttittel = "Avslag: Tilskudd ved kjøp av briller til barn",
    ),
    VEDTAKSBREV_BARNEBRILLER_HOTSAK_INNVILGELSE(
        kode = "vedtaksbrev_barnebriller_hotsak_innvilgelse",
        dokumenttittel = "Innvilgelse: Tilskudd ved kjøp av briller til barn",
    ),
    INNHENTE_OPPLYSNINGER_BARNEBRILLER(
        kode = "innhente_opplysninger_barnebriller",
        dokumenttittel = "Briller til barn: Nav etterspør opplysninger",
    ),
    NOTAT(
        kode = "HJE_NOT_001",
        dokumenttittel = "Journalført notat i sak",
    ),
    BREVEDITOR_VEDTAKSBREV(
        kode = "vedtaksbrev_hotsak_breveditor",
        dokumenttittel = "Vedtak for søknad om hjelpemidler",
    ),
    BREVEDITOR_SVARTIDSBREV(
        kode = "svartidsbrev_hotsak_breveditor",
        dokumenttittel = "Brev om forventet saksbehandlingstid",
    ),
    BREVEDITOR_INNHENTE_OPPLYSNINGER(
        kode = "innhente_opplysninger_hotsak_breveditor",
        dokumenttittel = "Brev om etterspørsel av opplysninger",
    )
    ;

    constructor(skjema: NavSkjema, dokumenttittel: String = skjema.beskrivelse) : this(
        kode = skjema.kode,
        dokumenttittel = dokumenttittel,
    )
}

val navSkjemaEttersendelseByDokumenttype: Map<Dokumenttype, NavSkjema> = mapOf(
    Dokumenttype.INNHENTE_OPPLYSNINGER_BARNEBRILLER to NavSkjema.NAV_10_07_34,
)
