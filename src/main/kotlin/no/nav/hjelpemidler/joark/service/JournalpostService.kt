package no.nav.hjelpemidler.joark.service

import com.fasterxml.jackson.databind.JsonNode
import mu.KotlinLogging
import no.nav.hjelpemidler.dokarkiv.DokarkivClient
import no.nav.hjelpemidler.dokarkiv.avsenderMottakerMedFnr
import no.nav.hjelpemidler.dokarkiv.brukerMedFnr
import no.nav.hjelpemidler.dokarkiv.fagsakHjelpemidler
import no.nav.hjelpemidler.dokarkiv.models.AvsenderMottaker
import no.nav.hjelpemidler.dokarkiv.models.Bruker
import no.nav.hjelpemidler.dokarkiv.models.Dokument
import no.nav.hjelpemidler.dokarkiv.models.DokumentInfo
import no.nav.hjelpemidler.dokarkiv.models.DokumentVariant
import no.nav.hjelpemidler.dokarkiv.models.FerdigstillJournalpostRequest
import no.nav.hjelpemidler.dokarkiv.models.KnyttTilAnnenSakRequest
import no.nav.hjelpemidler.dokarkiv.models.OppdaterJournalpostRequest
import no.nav.hjelpemidler.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.dokarkiv.models.Sak
import no.nav.hjelpemidler.http.withCorrelationId
import no.nav.hjelpemidler.joark.jsonMapper
import no.nav.hjelpemidler.joark.metrics.Prometheus
import no.nav.hjelpemidler.joark.pdf.PdfClient
import no.nav.hjelpemidler.joark.service.barnebriller.JournalpostBarnebrillevedtakData
import no.nav.hjelpemidler.saf.SafClient
import no.nav.hjelpemidler.saf.enums.AvsenderMottakerIdType
import no.nav.hjelpemidler.saf.enums.BrukerIdType
import no.nav.hjelpemidler.saf.enums.Journalposttype
import no.nav.hjelpemidler.saf.enums.Journalstatus
import no.nav.hjelpemidler.saf.enums.Tema
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
        fnrBruker: String = fnrAvsender,
        forsøkFerdigstill: Boolean = false,
        block: OpprettJournalpostRequestConfigurer.() -> Unit = {},
    ): String = withCorrelationId {
        val lagOpprettJournalpostRequest = OpprettJournalpostRequestConfigurer(
            fnrBruker = fnrBruker,
            fnrAvsenderMottaker = fnrAvsender,
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
        fnrMottaker: String,
        fnrBruker: String = fnrMottaker,
        forsøkFerdigstill: Boolean = false,
        block: OpprettJournalpostRequestConfigurer.() -> Unit = {},
    ): String = withCorrelationId {
        val lagOpprettJournalpostRequest = OpprettJournalpostRequestConfigurer(
            fnrBruker = fnrBruker,
            fnrAvsenderMottaker = fnrMottaker,
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
            forsøkFerdigstill = false,
        ) {
            dokument(
                fysiskDokument = fysiskDokument,
                dokumenttype = dokumenttype,
                dokumenttittel = dokumenttittel,
            )
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

    suspend fun kopierJournalpost(
        journalpostId: String,
        nyEksternReferanseId: String,
        journalførendeEnhet: String? = null,
    ): String =
        withCorrelationId("journalpostId" to journalpostId, "nyEksternReferanseId" to nyEksternReferanseId) {
            val journalpost = hentJournalpost(journalpostId)

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
                avsenderMottaker = journalpost.avsenderMottaker.toDokarkiv(),
                behandlingstema = journalpost.behandlingstema,
                bruker = journalpost.bruker.toDokarkiv(),
                datoDokument = journalpost.datoOpprettet,
                eksternReferanseId = nyEksternReferanseId,
                journalfoerendeEnhet = journalførendeEnhet ?: journalpost.journalfoerendeEnhet,
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

    suspend fun ferdigstillJournalpost(
        journalpostId: String,
        journalførendeEnhet: String,
        fnrBruker: String,
        sakId: String,
        dokumentId: String?,
        dokumenttittel: String?,
    ): String {
        val journalpost = hentJournalpost(journalpostId)
        val journalstatus = journalpost.journalstatus

        log.info {
            "Ferdigstiller journalpost med journalpostId: $journalpostId, journalstatus: $journalstatus"
        }

        val dokumenter = when {
            dokumentId == null || dokumenttittel == null -> null
            else -> listOf(
                DokumentInfo(
                    dokumentInfoId = dokumentId,
                    tittel = dokumenttittel,
                )
            )
        }

        return when (journalstatus) {
            Journalstatus.MOTTATT -> {
                dokarkivClient.oppdaterJournalpost(
                    journalpostId = journalpostId,
                    oppdaterJournalpostRequest = OppdaterJournalpostRequest(
                        avsenderMottaker = avsenderMottakerMedFnr(fnrBruker),
                        bruker = brukerMedFnr(fnrBruker),
                        sak = fagsakHjelpemidler(sakId),
                        tema = Tema.HJE.toString(),
                        dokumenter = dokumenter,
                    )
                )

                dokarkivClient.ferdigstillJournalpost(
                    journalpostId = journalpostId,
                    ferdigstillJournalpostRequest = FerdigstillJournalpostRequest(
                        journalfoerendeEnhet = journalførendeEnhet,
                    )
                )

                journalpostId
            }

            Journalstatus.FEILREGISTRERT,
            Journalstatus.JOURNALFOERT,
            -> {
                log.info {
                    "Journalpost er allerede journalført eller feilregistrert, knytter til annen sak, journalpostId: $journalpostId, sakId: $sakId"
                }

                val knyttTilAnnenSakResponse = dokarkivClient.knyttTilAnnenSak(
                    journalpostId = journalpostId,
                    knyttTilAnnenSakRequest = KnyttTilAnnenSakRequest(
                        bruker = brukerMedFnr(fnrBruker),
                        fagsakId = sakId,
                        fagsaksystem = Sak.Fagsaksystem.HJELPEMIDLER.toString(),
                        journalfoerendeEnhet = journalførendeEnhet,
                        sakstype = KnyttTilAnnenSakRequest.Sakstype.FAGSAK,
                        tema = Tema.HJE.toString(),
                    )
                )

                val nyJournalpostId = checkNotNull(knyttTilAnnenSakResponse.nyJournalpostId) {
                    "Mottok ikke nyJournalpostId etter å ha knyttet journalpostId: $journalpostId til sakId: $sakId"
                }.toString()

                log.info { "Knyttet journalpost til annen sak, journalpostId: $journalpostId, nyJournalpostId: $nyJournalpostId, sakId: $sakId" }

                if (dokumenter != null) {
                    dokarkivClient.oppdaterJournalpost(
                        nyJournalpostId,
                        OppdaterJournalpostRequest(dokumenter = dokumenter)
                    )
                }

                nyJournalpostId
            }

            else -> error("Mangler støtte for å ferdigstille journalpost med journalstatus: $journalstatus")
        }
    }

    suspend fun hentJournalpost(journalpostId: String): Journalpost =
        checkNotNull(safClient.hentJournalpost(journalpostId)) {
            "Fant ikke journalpost med journalpostId: $journalpostId"
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

    private fun no.nav.hjelpemidler.saf.hentjournalpost.AvsenderMottaker?.toDokarkiv(): AvsenderMottaker? =
        when {
            this == null -> null
            id == null -> null
            type == null -> null
            else -> AvsenderMottaker(id, type.toDokarkiv())
        }

    private fun no.nav.hjelpemidler.saf.hentjournalpost.Bruker?.toDokarkiv(): Bruker? =
        when {
            this == null -> null
            id == null -> null
            type == null -> null
            else -> Bruker(id, type.toDokarkiv())
        }
}
