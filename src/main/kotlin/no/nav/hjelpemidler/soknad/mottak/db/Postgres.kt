package no.nav.hjelpemidler.soknad.mottak.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.hjelpemidler.soknad.mottak.Configuration
import no.nav.hjelpemidler.soknad.mottak.Profile
import org.flywaydb.core.Flyway

internal fun migrate(config: Configuration) =
    HikariDataSource(hikariConfigFrom(config)).use { migrate(it) }

internal fun hikariConfigFrom(config: Configuration) =
    HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://${config.database.host}:${config.database.port}/${config.database.name}"
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
        config.database.user?.let { username = it }
        config.database.password?.let { password = it }
    }

internal fun dataSourceFrom(config: Configuration): HikariDataSource = when (config.application.profile) {
    Profile.LOCAL -> HikariDataSource(hikariConfigFrom(config))
    else -> HikariDataSource(hikariConfigFrom(config))
}

internal fun migrate(dataSource: HikariDataSource, initSql: String = ""): Int =
    Flyway.configure().dataSource(dataSource).initSql(initSql).load().migrate()

internal fun clean(dataSource: HikariDataSource) = Flyway.configure().dataSource(dataSource).load().clean()
