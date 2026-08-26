package no.nav.hjelpemidler.joark.dokarkiv.models

data class EndretDokument(
    val dokumentId: String,
    val tittel: String,
    val annetInnhold: Set<String> = emptySet(),
) {
    fun tilDokumentInfo(): DokumentInfo = DokumentInfo(dokumentInfoId = dokumentId, tittel = tittel)
}
