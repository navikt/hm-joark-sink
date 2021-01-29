package no.nav.hjelpemidler.joark

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.hjelpemidler.joark.oppslag.PdfClient
import no.nav.hjelpemidler.joark.service.JoarkDataSink

fun main() {

    val pdfClient = PdfClient(Configuration.pdf.baseUrl)

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.rapidApplication))
        .build().apply {
            JoarkDataSink(this, pdfClient)
        }.start()
}
