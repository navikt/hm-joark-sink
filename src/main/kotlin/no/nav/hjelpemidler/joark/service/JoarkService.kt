package no.nav.hjelpemidler.joark.service

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.hjelpemidler.dokarkiv.DokarkivClient
import no.nav.hjelpemidler.dokarkiv.models.AvsenderMottaker
import no.nav.hjelpemidler.dokarkiv.models.Bruker
import no.nav.hjelpemidler.dokarkiv.models.Dokument
import no.nav.hjelpemidler.dokarkiv.models.DokumentVariant
import no.nav.hjelpemidler.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.saf.SafClient
import no.nav.hjelpemidler.saf.enums.AvsenderMottakerIdType
import no.nav.hjelpemidler.saf.enums.BrukerIdType
import no.nav.hjelpemidler.saf.enums.Journalposttype
import java.util.UUID

private val log = KotlinLogging.logger {}

class JoarkService(
    private val dokarkivClient: DokarkivClient,
    private val safClient: SafClient,
) {
    suspend fun feilregistrerJournalpost(journalpostId: String): String =
        withLoggingContext("journalpostId" to journalpostId) {
            log.info {
                "Feilregistrerer journalpost med journalpostId: $journalpostId"
            }
            dokarkivClient.feilregistrerSakstilknytning(journalpostId)
            journalpostId
        }

    suspend fun kopierJournalpost(søknadId: UUID, journalpostId: String): String =
        withLoggingContext("søknadId" to søknadId.toString(), "journalpostId" to journalpostId) {
            val journalpost = checkNotNull(safClient.hentJournalpost(journalpostId)) {
                "Fant ikke journalpost med journalpostId: $journalpostId"
            }

            log.info {
                "Kopierer journalpost med journalpostId: $journalpostId, eksternReferanseId: ${journalpost.eksternReferanseId}"
            }

            val dokumenter = journalpost.dokumenter?.filterNotNull()
                ?.map { dokument ->
                    val dokumentInfoId = dokument.dokumentInfoId
                    val dokumentvarianter = dokument.dokumentvarianter.filterNotNull().map {
                        val fysiskDokument = safClient.hentDokument(journalpostId, dokumentInfoId, it.variantformat)
                        DokumentVariant(
                            filtype = it.filtype ?: "PDFA",
                            fysiskDokument = listOf(fysiskDokument),
                            variantformat = it.variantformat.toString(),
                        )
                    }
                    Dokument(
                        brevkode = dokument.brevkode,
                        dokumentvarianter = dokumentvarianter,
                        tittel = dokument.tittel
                    )
                } ?: emptyList()

            val opprettJournalpostRequest = OpprettJournalpostRequest(
                dokumenter = dokumenter,
                journalposttype = journalpost.journalposttype.toDokarkiv(),
                avsenderMottaker = journalpost.avsenderMottaker?.let { avsenderMottaker ->
                    AvsenderMottaker(
                        id = avsenderMottaker.id,
                        idType = avsenderMottaker.type.toDokarkiv(),
                        navn = avsenderMottaker.navn
                    )
                },
                behandlingstema = journalpost.behandlingstema,
                bruker = journalpost.bruker?.let { bruker ->
                    Bruker(
                        id = checkNotNull(bruker.id) {
                            "journalpost.bruker.id i journalpost hentet fra SAF var null, journalpostId: $journalpostId"
                        },
                        idType = bruker.type.toDokarkiv(),
                    )
                },
                datoDokument = journalpost.datoOpprettet,
                eksternReferanseId = søknadId.toString() + "HOTSAK_TIL_GOSYS",
                journalfoerendeEnhet = journalpost.journalfoerendeEnhet,
                kanal = journalpost.kanal.toString(),
                tema = journalpost.tema.toString(),
                tittel = journalpost.tittel.toString(),
            )

            val opprettJournalpostResponse = dokarkivClient.opprettJournalpost(opprettJournalpostRequest)
            val nyJournalpostId = opprettJournalpostResponse.journalpostId
            log.info {
                "Kopierte journalpost med journalpostId: $journalpostId, nyJournalpostId: $nyJournalpostId"
            }
            nyJournalpostId
        }

    private fun Journalposttype?.toDokarkiv(): OpprettJournalpostRequest.Journalposttype =
        when (this) {
            Journalposttype.I -> OpprettJournalpostRequest.Journalposttype.INNGAAENDE
            Journalposttype.U -> OpprettJournalpostRequest.Journalposttype.UTGAAENDE
            Journalposttype.N -> OpprettJournalpostRequest.Journalposttype.NOTAT
            else -> error("Ukjent journalposttype: '$this'")
        }

    private fun AvsenderMottakerIdType?.toDokarkiv(): AvsenderMottaker.IdType =
        when (this) {
            AvsenderMottakerIdType.FNR -> AvsenderMottaker.IdType.FNR
            AvsenderMottakerIdType.ORGNR -> AvsenderMottaker.IdType.ORGNR
            AvsenderMottakerIdType.HPRNR -> AvsenderMottaker.IdType.HPRNR
            AvsenderMottakerIdType.UTL_ORG -> AvsenderMottaker.IdType.UTL_ORG
            else -> error("Ukjent avsenderMottakerIdType: '$this'")
        }

    private fun BrukerIdType?.toDokarkiv(): Bruker.IdType =
        when (this) {
            BrukerIdType.FNR -> Bruker.IdType.FNR
            BrukerIdType.ORGNR -> Bruker.IdType.ORGNR
            BrukerIdType.AKTOERID -> Bruker.IdType.AKTOERID
            else -> error("Ukjent brukerIdType: '$this'")
        }
}
