package no.nav.hjelpemidler.joark.service

import com.fasterxml.jackson.databind.JsonNode
import mu.KotlinLogging
import no.nav.hjelpemidler.dokarkiv.DokarkivClient
import no.nav.hjelpemidler.dokarkiv.models.AvsenderMottaker
import no.nav.hjelpemidler.dokarkiv.models.Bruker
import no.nav.hjelpemidler.dokarkiv.models.Dokument
import no.nav.hjelpemidler.dokarkiv.models.DokumentInfo
import no.nav.hjelpemidler.dokarkiv.models.DokumentVariant
import no.nav.hjelpemidler.dokarkiv.models.OppdaterJournalpostRequest
import no.nav.hjelpemidler.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.http.withCorrelationId
import no.nav.hjelpemidler.joark.jsonMapper
import no.nav.hjelpemidler.joark.metrics.Prometheus
import no.nav.hjelpemidler.joark.pdf.PdfClient
import no.nav.hjelpemidler.joark.service.barnebriller.JournalpostBarnebrillevedtakData
import no.nav.hjelpemidler.saf.SafClient
import no.nav.hjelpemidler.saf.enums.AvsenderMottakerIdType
import no.nav.hjelpemidler.saf.enums.BrukerIdType
import no.nav.hjelpemidler.saf.enums.Journalposttype
import no.nav.hjelpemidler.saf.hentjournalpost.Journalpost
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

class JournalpostService(
    private val pdfClient: PdfClient,
    private val dokarkivClient: DokarkivClient,
    private val safClient: SafClient,
) {
    suspend fun genererPdf(søknadJson: JsonNode): ByteArray {
        val fysiskDokument = pdfClient.genererSøknadPdf(
            jsonMapper.writeValueAsString(søknadJson),
        )
        Prometheus.pdfGenerertCounter.inc()
        return fysiskDokument
    }

    suspend fun genererPdf(data: JournalpostBarnebrillevedtakData): ByteArray {
        val fysiskDokument = pdfClient.genererBarnebrillePdf(
            jsonMapper.writeValueAsString(data),
        )
        Prometheus.pdfGenerertCounter.inc()
        return fysiskDokument
    }

    suspend fun opprettInngåendeJournalpost(
        fnrAvsender: String,
        navnAvsender: String,
        fnrBruker: String = fnrAvsender,
        forsøkFerdigstill: Boolean = false,
        block: OpprettJournalpostRequestConfigurer.() -> Unit = {},
    ): String = withCorrelationId {
        val lagOpprettJournalpostRequest = OpprettJournalpostRequestConfigurer(
            fnrBruker = fnrBruker,
            fnrAvsender = fnrAvsender,
            navnAvsender = navnAvsender,
            journalposttype = OpprettJournalpostRequest.Journalposttype.INNGAAENDE,
        ).apply(block)
        val journalpost = dokarkivClient.opprettJournalpost(
            opprettJournalpostRequest = lagOpprettJournalpostRequest(),
            forsøkFerdigstill = forsøkFerdigstill
        )
        val journalpostId = journalpost.journalpostId
        val eksternReferanseId = lagOpprettJournalpostRequest.eksternReferanseId
        val ferdigstilt = journalpost.journalpostferdigstilt
        val datoMottatt = lagOpprettJournalpostRequest.datoMottatt
        log.info {
            "Inngående journalpost opprettet, journalpostId: $journalpostId, eksternReferanseId: $eksternReferanseId, ferdigstilt: $ferdigstilt, datoMottatt: $datoMottatt"
        }
        Prometheus.opprettetOgFerdigstiltJournalpostCounter.inc()
        journalpostId
    }

    suspend fun opprettUtgåendeJournalpost(
        fnrAvsender: String,
        navnAvsender: String,
        fnrBruker: String = fnrAvsender,
        forsøkFerdigstill: Boolean = false,
        block: OpprettJournalpostRequestConfigurer.() -> Unit = {},
    ): String = withCorrelationId {
        val lagOpprettJournalpostRequest = OpprettJournalpostRequestConfigurer(
            fnrBruker = fnrBruker,
            fnrAvsender = fnrAvsender,
            navnAvsender = navnAvsender,
            journalposttype = OpprettJournalpostRequest.Journalposttype.UTGAAENDE
        ).apply(block)
        val journalpost = dokarkivClient.opprettJournalpost(
            opprettJournalpostRequest = lagOpprettJournalpostRequest(),
            forsøkFerdigstill = forsøkFerdigstill
        )
        val journalpostId = journalpost.journalpostId
        val eksternReferanseId = lagOpprettJournalpostRequest.eksternReferanseId
        val ferdigstilt = journalpost.journalpostferdigstilt
        log.info {
            "Utgående journalpost opprettet, journalpostId: $journalpostId, eksternReferanseId: $eksternReferanseId, ferdigstilt: $ferdigstilt"
        }
        Prometheus.opprettetOgFerdigstiltJournalpostCounter.inc()
        journalpostId
    }

    suspend fun arkiverSøknad(
        fnrBruker: String,
        navnBruker: String,
        søknadId: UUID,
        søknadJson: JsonNode,
        sakstype: Sakstype,
        dokumenttittel: String,
        eksternReferanseId: String,
        datoMottatt: LocalDateTime? = null,
    ): String = withCorrelationId(
        "søknadId" to søknadId.toString(),
        "sakstype" to sakstype.name,
        "eksternReferanseId" to eksternReferanseId,
        "datoMottatt" to datoMottatt?.toString(),
    ) {
        log.info {
            "Arkiverer søknad, søknadId: $søknadId, sakstype: $sakstype, eksternReferanseId: $eksternReferanseId, datoMottatt: $datoMottatt"
        }
        val fysiskDokument = genererPdf(søknadJson)
        val dokumenttype = sakstype.dokumenttype
        val journalpostId = opprettInngåendeJournalpost(
            fnrAvsender = fnrBruker,
            navnAvsender = navnBruker,
            forsøkFerdigstill = false,
        ) {
            dokument(fysiskDokument, dokumenttype, dokumenttittel)
            tittelFra(dokumenttype)
            this.eksternReferanseId = eksternReferanseId
            this.datoMottatt = datoMottatt
            this.journalførendeEnhet = null
        }
        log.info {
            "Søknad ble arkivert, søknadId: $søknadId, sakstype: $sakstype, journalpostId: $journalpostId, eksternReferanseId: $eksternReferanseId"
        }
        Prometheus.søknadArkivertCounter.inc()
        journalpostId
    }

    suspend fun feilregistrerSakstilknytning(journalpostId: String) =
        withCorrelationId("journalpostId" to journalpostId) {
            log.info {
                "Feilregistrerer journalpost med journalpostId: $journalpostId"
            }
            dokarkivClient.feilregistrerSakstilknytning(journalpostId)
            Prometheus.feilregistrerteSakstilknytningForJournalpostCounter.inc()
        }

    suspend fun kopierJournalpost(journalpostId: String, nyEksternReferanseId: String): String =
        withCorrelationId("journalpostId" to journalpostId, "nyEksternReferanseId" to nyEksternReferanseId) {
            val journalpost = checkNotNull(hentJournalpost(journalpostId)) {
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
                            fysiskDokument = fysiskDokument,
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
                eksternReferanseId = nyEksternReferanseId,
                journalfoerendeEnhet = journalpost.journalfoerendeEnhet,
                kanal = journalpost.kanal.toString(),
                tema = journalpost.tema.toString(),
                tittel = journalpost.tittel.toString(),
            )

            val opprettJournalpostResponse = dokarkivClient.opprettJournalpost(
                opprettJournalpostRequest = opprettJournalpostRequest,
                forsøkFerdigstill = false
            )
            val nyJournalpostId = opprettJournalpostResponse.journalpostId
            log.info {
                "Kopierte journalpost med journalpostId: $journalpostId, nyJournalpostId: $nyJournalpostId, nyEksternReferanseId: $nyEksternReferanseId"
            }
            nyJournalpostId
        }

    suspend fun endreTittel(
        journalpostId: String,
        tittel: String,
        dokumenter: List<DokumentInfo>,
    ): String = withCorrelationId("journalpostId" to journalpostId) {
        log.info {
            "Endrer tittel på journalpost med journalpostId: $journalpostId"
        }
        val oppdaterJournalpostRequest = OppdaterJournalpostRequest(tittel = tittel, dokumenter = dokumenter)
        val oppdaterJournalpostResponse = dokarkivClient.oppdaterJournalpost(journalpostId, oppdaterJournalpostRequest)
        oppdaterJournalpostResponse.journalpostId
    }

    suspend fun hentJournalpost(journalpostId: String): Journalpost? =
        safClient.hentJournalpost(journalpostId)

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
