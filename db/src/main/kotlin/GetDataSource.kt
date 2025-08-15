package com.moshy.drugcalc.db

import com.moshy.drugcalc.types.datasource.DBConfig
import com.moshy.drugcalc.types.datasource.DataSourceDelegate

import com.zaxxer.hikari.*
import com.zaxxer.hikari.pool.HikariPool
import org.flywaydb.core.Flyway
import kotlin.concurrent.atomics.*

fun getDataSource(c: DBConfig): DataSourceDelegate {
    val config = HikariConfig().apply {
        jdbcUrl = "jdbc:${c.driver}:${c.url}"
        if (c.user != null)
            username = c.user
        if (c.password != null)
            password = c.password
        if (c.driver == "sqlite") {
            addDataSourceProperty("journal_mode", "wal")
            addDataSourceProperty("foreign_keys", "on")
        }
        c.maxPoolSize?.let { maximumPoolSize = it }
        c.connectTimeout?.let { connectionTimeout = it }
    }
    val source = HikariDataSource(config)
    return JooqDataSource(source)
}

/** Extracts (message, cause) from caught exception if it was caused due to failure to initialize DB connection. */
fun getDataInitFailure(caught: Throwable) =
    (caught as? HikariPool.PoolInitializationException)?.let { caught.message!! to caught.cause!! }
