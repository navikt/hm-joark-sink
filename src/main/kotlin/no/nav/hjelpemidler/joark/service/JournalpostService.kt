package no.nav.hjelpemidler.joark.service

import com.fasterxml.jackson.databind.JsonNode
import mu.KotlinLogging
import no.nav.hjelpemidler.http.withCorrelationId
import no.nav.hjelpemidler.joark.dokarkiv.DokarkivClient
import no.nav.hjelpemidler.joark.dokarkiv.OpprettJournalpostRequestConfigurer
import no.nav.hjelpemidler.joark.dokarkiv.avsenderMottakerMedFnr
import no.nav.hjelpemidler.joark.dokarkiv.brukerMedFnr
import no.nav.hjelpemidler.joark.dokarkiv.fagsakHjelpemidler
import no.nav.hjelpemidler.joark.dokarkiv.models.AvsenderMottaker
import no.nav.hjelpemidler.joark.dokarkiv.models.Bruker
import no.nav.hjelpemidler.joark.dokarkiv.models.Dokument
import no.nav.hjelpemidler.joark.dokarkiv.models.DokumentInfo
import no.nav.hjelpemidler.joark.dokarkiv.models.DokumentVariant
import no.nav.hjelpemidler.joark.dokarkiv.models.FerdigstillJournalpostRequest
import no.nav.hjelpemidler.joark.dokarkiv.models.KnyttTilAnnenSakRequest
import no.nav.hjelpemidler.joark.dokarkiv.models.OppdaterJournalpostRequest
import no.nav.hjelpemidler.joark.dokarkiv.models.OpprettJournalpostRequest
import no.nav.hjelpemidler.joark.dokarkiv.models.Sak
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.domain.Sakstype
import no.nav.hjelpemidler.joark.jsonMapper
import no.nav.hjelpemidler.joark.metrics.Prometheus
import no.nav.hjelpemidler.joark.pdf.FørstesidegeneratorClient
import no.nav.hjelpemidler.joark.pdf.OpprettFørstesideRequestConfigurer
import no.nav.hjelpemidler.joark.pdf.PdfGeneratorClient
import no.nav.hjelpemidler.joark.pdf.SøknadPdfGeneratorClient
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
    private val pdfGeneratorClient: PdfGeneratorClient,
    private val søknadPdfGeneratorClient: SøknadPdfGeneratorClient,
    private val dokarkivClient: DokarkivClient,
    private val safClient: SafClient,
    private val førstesidegeneratorClient: FørstesidegeneratorClient,
) {
    suspend fun genererPdf(søknadJson: JsonNode): ByteArray {
        val fysiskDokument = søknadPdfGeneratorClient.genererPdfSøknad(
            jsonMapper.writeValueAsString(søknadJson),
        )

        Prometheus.pdfGenerertCounter.inc()

        return fysiskDokument
    }

    suspend fun genererPdf(data: JournalpostBarnebrillevedtakData): ByteArray {
        val fysiskDokument = søknadPdfGeneratorClient.genererPdfBarnebriller(
            jsonMapper.writeValueAsString(data),
        )

        Prometheus.pdfGenerertCounter.inc()

        return fysiskDokument
    }

    suspend fun genererFørsteside(
        tittel: String,
        fnrBruker: String,
        dokument: ByteArray,
        block: OpprettFørstesideRequestConfigurer.() -> Unit = {},
    ): ByteArray {
        val lagRequest = OpprettFørstesideRequestConfigurer(tittel, fnrBruker).apply(block)

        log.info { "Lager førsteside til dokument, tittel: '${lagRequest.tittel}', brevkode: ${lagRequest.brevkode}" }

        val førsteside = førstesidegeneratorClient.lagFørsteside(lagRequest())

        return pdfGeneratorClient.kombinerPdf(dokument, førsteside.fysiskDokument)
    }

    suspend fun opprettInngåendeJournalpost(
        fnrAvsender: String,
        fnrBruker: String = fnrAvsender,
        dokumenttype: Dokumenttype,
        forsøkFerdigstill: Boolean = false,
        block: OpprettJournalpostRequestConfigurer.() -> Unit = {},
    ) = withCorrelationId {
        val lagOpprettJournalpostRequest = OpprettJournalpostRequestConfigurer(
            fnrBruker = fnrBruker,
            fnrAvsenderMottaker = fnrAvsender,
            dokumenttype = dokumenttype,
            journalposttype = OpprettJournalpostRequest.Journalposttype.INNGAAENDE,
        ).apply(block)

        val journalpost = dokarkivClient.opprettJournalpost(
            opprettJournalpostRequest = lagOpprettJournalpostRequest(),
            forsøkFerdigstill = forsøkFerdigstill,
        )

        val journalpostId = journalpost.journalpostId
        val eksternReferanseId = lagOpprettJournalpostRequest.eksternReferanseId
        val ferdigstilt = journalpost.journalpostferdigstilt
        val datoMottatt = lagOpprettJournalpostRequest.datoMottatt

        log.info {
            "Inngående journalpost opprettet, journalpostId: $journalpostId, eksternReferanseId: $eksternReferanseId, ferdigstilt: $ferdigstilt, datoMottatt: $datoMottatt"
        }

        Prometheus.opprettetOgFerdigstiltJournalpostCounter.inc()

        journalpost
    }

    suspend fun opprettUtgåendeJournalpost(
        fnrMottaker: String,
        fnrBruker: String = fnrMottaker,
        dokumenttype: Dokumenttype,
        forsøkFerdigstill: Boolean = false,
        block: OpprettJournalpostRequestConfigurer.() -> Unit = {},
    ): String = withCorrelationId {
        val lagOpprettJournalpostRequest = OpprettJournalpostRequestConfigurer(
            fnrBruker = fnrBruker,
            fnrAvsenderMottaker = fnrMottaker,
            dokumenttype = dokumenttype,
            journalposttype = OpprettJournalpostRequest.Journalposttype.UTGAAENDE,
        ).apply(block)

        val journalpost = dokarkivClient.opprettJournalpost(
            opprettJournalpostRequest = lagOpprettJournalpostRequest(),
            forsøkFerdigstill = forsøkFerdigstill,
            opprettetAv = lagOpprettJournalpostRequest.opprettetAv
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
        val journalpostId = opprettInngåendeJournalpost(
            fnrAvsender = fnrBruker,
            dokumenttype = Dokumenttype.SØKNAD_OM_HJELPEMIDLER,
            forsøkFerdigstill = false,
        ) {
            dokument(
                fysiskDokument = fysiskDokument,
                dokumenttittel = dokumenttittel,
            )
            this.eksternReferanseId = eksternReferanseId
            this.datoMottatt = datoMottatt
            this.journalførendeEnhet = null
        }.journalpostId

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
                "Kopierer journalpost med journalpostId: $journalpostId, eksternReferanseId: ${journalpost.eksternReferanseId}, type: ${journalpost.journalposttype}, status: ${journalpost.journalstatus}, kanal: ${journalpost.kanal}"
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
                        tittel = dokument.tittel,
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
                forsøkFerdigstill = false,
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


    suspend fun hentJournalposterForSak(
        sakId: String,
    ): List<no.nav.hjelpemidler.saf.hentdokumentoversiktsak.Journalpost> = withCorrelationId("sakId" to sakId) {
        log.info {
            "Henter journalposter for sakId: $sakId"
        }
        safClient.hentJournalposterForSak(sakId)
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
            "Ferdigstiller journalpost med journalpostId: $journalpostId, journalstatus: $journalstatus, journaltittel: ${journalpost.tittel}"
        }

        val dokumenter = when {
            dokumentId == null || dokumenttittel == null -> null
            else -> listOf(
                DokumentInfo(
                    dokumentInfoId = dokumentId,
                    tittel = dokumenttittel,
                ),
            )
        }

        return when (journalstatus) {
            Journalstatus.MOTTATT -> {
                if (journalpost.tittel == null && journalpostId in listOf(
                        "626556692",
                        "626442819",
                        "626419519",
                        "627280086",
                        "627777403",
                        "627911450",
                    )
                ) {
                    log.info("Patch tittel på journalposter uten tittel $journalpostId")
                    val overskrivTittel = "NAV 10-07.34 Tilskudd ved kjøp av briller til barn"
                    dokarkivClient.oppdaterJournalpost(
                        journalpostId = journalpostId,
                        oppdaterJournalpostRequest = OppdaterJournalpostRequest(
                            avsenderMottaker = avsenderMottakerMedFnr(fnrBruker),
                            bruker = brukerMedFnr(fnrBruker),
                            sak = fagsakHjelpemidler(sakId),
                            tema = Tema.HJE.toString(),
                            dokumenter = dokumenter?.map { dok ->
                                if (dok.tittel.isNullOrBlank()) {
                                    dok.copy(
                                        tittel = overskrivTittel
                                    )
                                } else {
                                    dok
                                }
                            },
                            tittel = overskrivTittel,
                        ),
                    )
                } else {
                    dokarkivClient.oppdaterJournalpost(
                        journalpostId = journalpostId,
                        oppdaterJournalpostRequest = OppdaterJournalpostRequest(
                            avsenderMottaker = avsenderMottakerMedFnr(fnrBruker),
                            bruker = brukerMedFnr(fnrBruker),
                            sak = fagsakHjelpemidler(sakId),
                            tema = Tema.HJE.toString(),
                            dokumenter = dokumenter,
                        ),
                    )
                }

                dokarkivClient.ferdigstillJournalpost(
                    journalpostId = journalpostId,
                    ferdigstillJournalpostRequest = FerdigstillJournalpostRequest(
                        journalfoerendeEnhet = journalførendeEnhet,
                    ),
                )

                journalpostId
            }

            Journalstatus.FEILREGISTRERT,
            Journalstatus.FERDIGSTILT,
            Journalstatus.JOURNALFOERT,
            -> {
                log.info {
                    "Journalpost har status: $journalstatus, knytter til annen sak, journalpostId: $journalpostId, sakId: $sakId"
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
                    ),
                )

                val nyJournalpostId = checkNotNull(knyttTilAnnenSakResponse.nyJournalpostId) {
                    "Mottok ikke nyJournalpostId etter å ha knyttet journalpostId: $journalpostId til sakId: $sakId"
                }.toString()

                log.info { "Knyttet journalpost til annen sak, journalpostId: $journalpostId, nyJournalpostId: $nyJournalpostId, sakId: $sakId" }

                if (dokumenter != null) {
                    dokarkivClient.oppdaterJournalpost(
                        nyJournalpostId,
                        OppdaterJournalpostRequest(dokumenter = dokumenter),
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
