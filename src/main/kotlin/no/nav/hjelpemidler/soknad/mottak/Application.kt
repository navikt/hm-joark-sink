package no.nav.hjelpemidler.soknad.mottak

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.soknad.mottak.db.SoknadStorePostgres
import no.nav.hjelpemidler.soknad.mottak.db.dataSourceFrom
import no.nav.hjelpemidler.soknad.mottak.db.migrate
import no.nav.hjelpemidler.soknad.mottak.service.SoknadDataSink

fun main() {
    val store = SoknadStorePostgres(dataSourceFrom(Configuration))

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(Configuration.rapidApplication))
        .build().apply {
            SoknadDataSink(this, store)
        }.apply {
            register(
                object : RapidsConnection.StatusListener {
                    override fun onStartup(rapidsConnection: RapidsConnection) {
                        migrate(Configuration)
                    }
                }
            )
        }.start()
}
