package com.moshy.drugcalc.dbtest

import com.moshy.drugcalc.db.getDataSource
import com.moshy.drugcalc.types.datasource.DBConfig
import com.moshy.drugcalc.types.datasource.DataSourceDelegate
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * A single PostgreSQL container shared by every test running in this JVM.
 *
 * Each call to [createDataSource] creates a uniquely-named database inside the container, so tests
 * get an ephemeral database with the same lifetime as the controller they build. [cleanup] closes
 * the pools and drops those databases after each test.
 */
internal object TestPostgres {
    private const val USER = "drugcalc"
    private const val PASSWORD = "drugcalc"
    private const val PORT = 5432

    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:16-alpine")
            // Use the built-in maintenance database so we can create/drop per-test databases.
            .withDatabaseName("postgres")
            .withUsername(USER)
            .withPassword(PASSWORD)
            .also { it.start() }
    }

    private val counter = AtomicInteger(0)

    private class LiveDb(val name: String, val source: DataSourceDelegate)

    // Ktor's test engine may build the datasource on a different thread than the JUnit test
    // thread, so tracking must not be thread-local. Tests in this project run sequentially.
    private val live: MutableList<LiveDb> = Collections.synchronizedList(mutableListOf())

    fun createDataSource(): DataSourceDelegate {
        val name = "dc_test_${counter.incrementAndGet()}"
        adminConnection().use { c ->
            c.createStatement().use { it.execute("""CREATE DATABASE "$name"""") }
        }
        val source = getDataSource(
            DBConfig(
                driver = "postgresql",
                url = "//${container.host}:${container.getMappedPort(PORT)}/$name",
                user = USER,
                password = PASSWORD,
            )
        )
        live.add(LiveDb(name, source))
        return source
    }

    /** Close every pool and drop every database created by the test that just finished. */
    fun cleanup() {
        while (true) {
            val db = synchronized(live) {
                if (live.isEmpty()) return
                live.removeAt(live.lastIndex)
            }
            runCatching { db.source.close() }
            runCatching {
                adminConnection().use { c ->
                    c.createStatement().use {
                        it.execute("""DROP DATABASE IF EXISTS "${db.name}" WITH (FORCE)""")
                    }
                }
            }
        }
    }

    private fun adminConnection(): Connection =
        DriverManager.getConnection(container.jdbcUrl, USER, PASSWORD)
}

/** Builds a fresh, migrated PostgreSQL-backed datasource for a single test. */
fun getTestingDataSource(): DataSourceDelegate = TestPostgres.createDataSource()
