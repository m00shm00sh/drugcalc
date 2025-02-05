package com.moshy.drugcalc

import kotlin.concurrent.thread
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.*
import org.jetbrains.exposed.sql.Database

import com.moshy.drugcalc.http.httpMainSetup
import com.moshy.drugcalc.repo.Repository
import com.moshy.drugcalc.util.AppConfig
import com.moshy.drugcalc.util.prepareDatabase

fun main(args: Array<String>) {
    // TODO: prepare a stderr logger for critical things before doing any IO
    val config = AppConfig.config
    val db = Database.connect(config.db)
    val repoConfig = prepareDatabase(db, config.repo)
    val repo = Repository(repoConfig, db)
    // free main thread for whatever other work needs to be done
    thread {
        embeddedServer(Jetty,
            port = config.server.port,
            host = config.server.host,
            module = { httpMainSetup(repo, config.limits).invoke(this) }
        ).start(wait = true)
    }
}