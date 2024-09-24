package no.nav.hjelpemidler.joark.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

object Prometheus {
    val registry: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val søknadArkivertCounter: Counter =
        registry.counter("hm_soknad_lagret_joark")

    val pdfGenerertCounter: Counter =
        registry.counter("hm_soknad_pdf_generert")

    val opprettetOgFerdigstiltJournalpostCounter: Counter =
        registry.counter("hm_ferdigstilt_journalpost_opprettet")

    val opprettetJournalpostMedStatusMottattCounter: Counter =
        registry.counter("hm_mottatt_journalpost_opprettet")

    val feilregistrerteSakstilknytningForJournalpostCounter: Counter =
        registry.counter("hm_feilregistrert_sakstilknytning_journalpost")
}
