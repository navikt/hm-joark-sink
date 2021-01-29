package no.nav.hjelpemidler.soknad.mottak.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import no.nav.hjelpemidler.soknad.mottak.metrics.Prometheus
import no.nav.hjelpemidler.soknad.mottak.service.SoknadData
import org.postgresql.util.PGobject
import javax.sql.DataSource

internal interface SoknadStore {
    fun save(soknadData: SoknadData): Int
}

internal class SoknadStorePostgres(private val ds: DataSource) : SoknadStore {
    companion object {
        private const val forwardLockKey = "soknad-forwarder"
        private val logger = KotlinLogging.logger {}
    }

    override fun save(soknadData: SoknadData): Int =
        time("insert_soknad") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        "INSERT INTO V1_SOKNAD (SOKNADS_ID,FNR_BRUKER, FNR_INNSENDER, DATA ) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",
                        soknadData.soknadId,
                        soknadData.fnrBruker,
                        soknadData.fnrInnsender,
                        PGobject().apply {
                            type = "jsonb"
                            value = soknadData.soknadJson
                        }
                    ).asUpdate
                )
            }
        }

    private inline fun <T : Any?> time(queryName: String, function: () -> T) =
        Prometheus.dbTimer.labels(queryName).startTimer().let { timer ->
            function().also {
                timer.observeDuration()
            }
        }
}
