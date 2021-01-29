package no.nav.hjelpemidler.joark

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.hjelpemidler.joark.service.JoarkDataSink

fun main() {

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.rapidApplication))
        .build().apply {
            JoarkDataSink(this)
        }.start()
}
