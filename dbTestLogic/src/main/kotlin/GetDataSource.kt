package com.moshy.drugcalc.dbtest

import com.moshy.drugcalc.db.getDataSource
import com.moshy.drugcalc.types.datasource.DBConfig
import com.moshy.drugcalc.types.datasource.DataSourceDelegate

import com.zaxxer.hikari.*
import org.flywaydb.core.Flyway
import kotlin.concurrent.atomics.*

@OptIn(ExperimentalAtomicApi::class)
fun getTestingDataSource(): DataSourceDelegate {
    /* SQLite :memory: is a per-connection temporary DB.
     * This means Flyway will be pointless and HikariCP won't work.
     * So, we must use a file. It's recommended that system tempdir is on a ramdisk or similar
    */
    val i = srcCount.fetchAndIncrement()
    val sqliteUrl = "${tmpDir}/dctest-$i.db"
    val flyway = Flyway.configure().run {
        dataSource(
            "jdbc:sqlite:$sqliteUrl", null, null
        )
        load()
    }
    flyway.migrate()
    return getDataSource(DBConfig(
        driver = "sqlite",
        url = sqliteUrl
    ))
}

@OptIn(ExperimentalAtomicApi::class)
private val srcCount = AtomicInt(0)
