package no.nav.hjelpemidler.joark.metrics

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

internal object Prometheus {
    val collectorRegistry = CollectorRegistry.defaultRegistry

    val soknadSendtCounter = Counter
        .build()
        .name("hm_soknad_lagret_joark")
        .help("Antall soknader lagret i joark")
        .register(collectorRegistry)
}
