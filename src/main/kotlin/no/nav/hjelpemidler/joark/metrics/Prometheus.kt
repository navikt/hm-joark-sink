package no.nav.hjelpemidler.joark.metrics

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

internal object Prometheus {
    private val collectorRegistry: CollectorRegistry =
        CollectorRegistry.defaultRegistry

    val søknadArkivertCounter: Counter = Counter
        .build()
        .name("hm_soknad_lagret_joark")
        .help("Antall soknader lagret i joark")
        .register(collectorRegistry)

    val pdfGenerertCounter: Counter = Counter
        .build()
        .name("hm_soknad_pdf_generert")
        .help("Antall pdf'er generert for lagring joark")
        .register(collectorRegistry)

    val opprettetOgFerdigstiltJournalpostCounter: Counter = Counter
        .build()
        .name("hm_ferdigstilt_journalpost_opprettet")
        .help("Antall opprettete+ferdigstilte journalposter i joark")
        .register(collectorRegistry)

    val opprettetJournalpostMedStatusMottattCounter: Counter = Counter
        .build()
        .name("hm_mottatt_journalpost_opprettet")
        .help("Antall opprettede + ferdigstilte journalposter i joark")
        .register(collectorRegistry)

    val feilregistrerteSakstilknytningForJournalpostCounter: Counter = Counter
        .build()
        .name("hm_feilregistrert_sakstilknytning_journalpost")
        .help("Antall feilregistrerte sakstilknytninger for journalposter i joark")
        .register(collectorRegistry)
}
