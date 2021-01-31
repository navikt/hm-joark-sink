package no.nav.hjelpemidler.joark.joark.metrics

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

internal object Prometheus {
    val collectorRegistry = CollectorRegistry.defaultRegistry

    val soknadArkivertCounter = Counter
        .build()
        .name("hm_soknad_lagret_joark")
        .help("Antall soknader lagret i joark")
        .register(collectorRegistry)

    val pdfGenerertCounter = Counter
        .build()
        .name("hm_soknad_pdf_generert")
        .help("Antall pdf'er generert for lagring joark")
        .register(collectorRegistry)
}
