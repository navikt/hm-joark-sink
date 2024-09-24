package no.nav.hjelpemidler.joark.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.http.withCorrelationId
import no.nav.hjelpemidler.joark.dokarkiv.DokarkivClient
import no.nav.hjelpemidler.joark.dokarkiv.OpprettJournalpostRequestConfigurer
import no.nav.hjelpemidler.joark.dokarkiv.avsenderMottakerMedFnr
import no.nav.hjelpemidler.joark.dokarkiv.brukerMedFnr
import no.nav.hjelpemidler.joark.dokarkiv.fagsakHjelpemidler
import no.nav.hjelpemidler.joark.dokarkiv.models.DokumentInfo
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
import no.nav.hjelpemidler.joark.pdf.SøknadApiClient
import no.nav.hjelpemidler.joark.pdf.SøknadPdfGeneratorClient
import no.nav.hjelpemidler.joark.service.barnebriller.JournalpostBarnebrillevedtakData
import no.nav.hjelpemidler.saf.SafClient
import no.nav.hjelpemidler.saf.enums.Journalstatus
import no.nav.hjelpemidler.saf.enums.Tema
import no.nav.hjelpemidler.saf.hentjournalpost.Journalpost
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}
private val secureLog = KotlinLogging.logger("tjenestekall")

class JournalpostService(
    private val pdfGeneratorClient: PdfGeneratorClient,
    private val søknadPdfGeneratorClient: SøknadPdfGeneratorClient,
    private val dokarkivClient: DokarkivClient,
    private val safClient: SafClient,
    private val førstesidegeneratorClient: FørstesidegeneratorClient,
    private val søknadApiClient: SøknadApiClient,
) {
    suspend fun hentBehovsmeldingPdf(id: UUID): ByteArray = søknadApiClient.hentPdf(id)

    suspend fun genererPdf(data: JournalpostBarnebrillevedtakData): ByteArray {
        val fysiskDokument = søknadPdfGeneratorClient.genererPdfBarnebriller(
            jsonMapper.writeValueAsString(data),
        )

        Prometheus.pdfGenerertCounter.increment()

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
        eksternReferanseId: String,
        forsøkFerdigstill: Boolean = false,
        block: OpprettJournalpostRequestConfigurer.() -> Unit = {},
    ) = withCorrelationId {
        val lagOpprettJournalpostRequest = OpprettJournalpostRequestConfigurer(
            fnrBruker = fnrBruker,
            fnrAvsenderMottaker = fnrAvsender,
            dokumenttype = dokumenttype,
            journalposttype = OpprettJournalpostRequest.Journalposttype.INNGAAENDE,
            eksternReferanseId = eksternReferanseId
        ).apply(block)

        val journalpost = dokarkivClient.opprettJournalpost(
            opprettJournalpostRequest = lagOpprettJournalpostRequest(),
            forsøkFerdigstill = forsøkFerdigstill,
        )

        val journalpostId = journalpost.journalpostId
        val ferdigstilt = journalpost.journalpostferdigstilt
        val datoMottatt = lagOpprettJournalpostRequest.datoMottatt

        log.info {
            "Inngående journalpost opprettet, journalpostId: $journalpostId, eksternReferanseId: $eksternReferanseId, ferdigstilt: $ferdigstilt, datoMottatt: $datoMottatt"
        }

        Prometheus.opprettetOgFerdigstiltJournalpostCounter.increment()

        journalpost
    }

    suspend fun opprettUtgåendeJournalpost(
        fnrMottaker: String,
        fnrBruker: String = fnrMottaker,
        dokumenttype: Dokumenttype,
        eksternReferanseId: String,
        forsøkFerdigstill: Boolean = false,
        block: OpprettJournalpostRequestConfigurer.() -> Unit = {},
    ): String = withCorrelationId {
        val lagOpprettJournalpostRequest = OpprettJournalpostRequestConfigurer(
            fnrBruker = fnrBruker,
            fnrAvsenderMottaker = fnrMottaker,
            dokumenttype = dokumenttype,
            journalposttype = OpprettJournalpostRequest.Journalposttype.UTGAAENDE,
            eksternReferanseId = eksternReferanseId,
        ).apply(block)

        val journalpost = dokarkivClient.opprettJournalpost(
            opprettJournalpostRequest = lagOpprettJournalpostRequest(),
            forsøkFerdigstill = forsøkFerdigstill,
            opprettetAv = lagOpprettJournalpostRequest.opprettetAv,
        )

        val journalpostId = journalpost.journalpostId
        val ferdigstilt = journalpost.journalpostferdigstilt

        log.info {
            "Utgående journalpost opprettet, journalpostId: $journalpostId, eksternReferanseId: $eksternReferanseId, ferdigstilt: $ferdigstilt"
        }

        Prometheus.opprettetOgFerdigstiltJournalpostCounter.increment()

        journalpostId
    }

    suspend fun arkiverBehovsmelding(
        fnrBruker: String,
        behovsmeldingId: UUID,
        sakstype: Sakstype,
        dokumenttittel: String,
        eksternReferanseId: String,
        datoMottatt: LocalDateTime? = null,
    ): String = withCorrelationId {
        log.info {
            "Arkiverer søknad, søknadId: $behovsmeldingId, sakstype: $sakstype, eksternReferanseId: $eksternReferanseId, datoMottatt: $datoMottatt"
        }

        val fysiskDokument = hentBehovsmeldingPdf(behovsmeldingId)
        val journalpostId = opprettInngåendeJournalpost(
            fnrAvsender = fnrBruker,
            dokumenttype = sakstype.dokumenttype,
            eksternReferanseId = eksternReferanseId,
            forsøkFerdigstill = false,
        ) {
            dokument(
                fysiskDokument = fysiskDokument,
                dokumenttittel = dokumenttittel,
            )
            this.datoMottatt = datoMottatt
            this.journalførendeEnhet = null
        }.journalpostId

        log.info {
            "${sakstype.name} ble arkivert, id: $behovsmeldingId, journalpostId: $journalpostId, eksternReferanseId: $eksternReferanseId"
        }

        Prometheus.søknadArkivertCounter.increment()

        journalpostId
    }

    suspend fun feilregistrerSakstilknytning(journalpostId: String) =
        withCorrelationId {
            log.info {
                "Feilregistrerer journalpost med journalpostId: $journalpostId"
            }
            dokarkivClient.feilregistrerSakstilknytning(journalpostId)
            Prometheus.feilregistrerteSakstilknytningForJournalpostCounter.increment()
        }

    /**
     * Brukes når vi vil at videre behandling av journalpost skal skje i Gosys / Infotrygd.
     */
    suspend fun kopierJournalpost(
        journalpostId: String,
        nyEksternReferanseId: String,
    ): String = withCorrelationId {
        val nyJournalpostId = dokarkivClient.kopierJournalpost(journalpostId, nyEksternReferanseId)
        log.info {
            "Kopierte journalpost med journalpostId: $journalpostId, nyJournalpostId: $nyJournalpostId, nyEksternReferanseId: $nyEksternReferanseId"
        }
        nyJournalpostId
    }

    suspend fun endreTittel(
        journalpostId: String,
        tittel: String,
        dokumenter: List<DokumentInfo>,
    ): String = withCorrelationId {
        log.info {
            "Endrer tittel på journalpost med journalpostId: $journalpostId"
        }

        val oppdaterJournalpostRequest = OppdaterJournalpostRequest(tittel = tittel, dokumenter = dokumenter)
        val oppdaterJournalpostResponse = dokarkivClient.oppdaterJournalpost(journalpostId, oppdaterJournalpostRequest)

        oppdaterJournalpostResponse.journalpostId
    }

    suspend fun hentJournalposterForSak(
        sakId: String,
    ): List<no.nav.hjelpemidler.saf.hentdokumentoversiktsak.Journalpost> = withCorrelationId {
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
                if (journalpost.tittel == null && journalpostId in setOf(
                        "626556692",
                        "626442819",
                        "626419519",
                        "627280086",
                        "627777403",
                        "627911450",
                    )
                ) {
                    log.info { "Patcher tittel på journalposter uten tittel, journalpostId: $journalpostId" }
                    val nyTittel = "NAV 10-07.34 Tilskudd ved kjøp av briller til barn"
                    dokarkivClient.oppdaterJournalpost(
                        journalpostId = journalpostId,
                        oppdaterJournalpostRequest = OppdaterJournalpostRequest(
                            avsenderMottaker = avsenderMottakerMedFnr(fnrBruker),
                            bruker = brukerMedFnr(fnrBruker),
                            sak = fagsakHjelpemidler(sakId),
                            tema = Tema.HJE.toString(),
                            dokumenter = dokumenter?.map { dokument ->
                                when {
                                    dokument.tittel.isNullOrBlank() -> dokument.copy(tittel = nyTittel)
                                    else -> dokument
                                }
                            },
                            tittel = nyTittel,
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

            else -> error("Mangler støtte for å ferdigstille journalpost med journalstatus: $journalstatus, journalpostId: $journalpostId")
        }
    }

    private suspend fun hentJournalpost(journalpostId: String): Journalpost =
        checkNotNull(safClient.hentJournalpost(journalpostId)) {
            "Fant ikke journalpost med journalpostId: $journalpostId"
        }
}
