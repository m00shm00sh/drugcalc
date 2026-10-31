package com.moshy.drugcalc.db

import com.moshy.drugcalc.types.datasource.DBConfig
import com.moshy.drugcalc.types.datasource.DataSourceDelegate

import com.zaxxer.hikari.*
import com.zaxxer.hikari.pool.HikariPool
import org.flywaydb.core.Flyway

fun getDataSource(c: DBConfig): DataSourceDelegate {
    val jdbcUrl = "jdbc:${c.driver}:${c.url}"
    runMigrations(jdbcUrl, c.user, c.password)
    val config = HikariConfig().apply {
        this.jdbcUrl = jdbcUrl
        if (c.user != null)
            username = c.user
        if (c.password != null)
            password = c.password
        if (c.driver == "postgresql") {
            // TIMESTAMP columns are interpreted as UTC by the query layer; make the server session
            // agree regardless of the host/container timezone.
            connectionInitSql = "SET TIME ZONE 'UTC'"
        }
        c.maxPoolSize?.let { maximumPoolSize = it }
        c.connectTimeout?.let { connectionTimeout = it }
    }
    val source = HikariDataSource(config)
    return JooqDataSource(source)
}

/** Apply all pending Flyway migrations for [jdbcUrl]. */
internal fun runMigrations(jdbcUrl: String, user: String?, password: String?) {
    Flyway.configure()
        .dataSource(jdbcUrl, user, password)
        // Keep TIMESTAMP defaults (CURRENT_TIMESTAMP) consistent with the application's UTC sessions.
        .initSql("SET TIME ZONE 'UTC'")
        .load()
        .migrate()
}

/** Extracts (message, cause) from caught exception if it was caused due to failure to initialize DB connection. */
fun getDataInitFailure(caught: Throwable) =
    (caught as? HikariPool.PoolInitializationException)?.let { caught.message!! to caught.cause!! }
