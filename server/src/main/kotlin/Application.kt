package com.moshy.drugcalc.server

import com.moshy.drugcalc.server.http.createKtorServer
import com.moshy.drugcalc.server.util.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    // we might have logging setup in config so to avoid logging chicken and egg, just print to stdout
    val cwd = System.getProperty("user.dir")
    println("workdir: $cwd")
    val config = AppConfig.config(args)
    config.http.bind.forEach {
        println("listening on: $it")
    }

    // we need IO dispatcher to prevent deadlock arising from request -> cache miss -> fetch -> db io
    runBlocking(Dispatchers.IO) {
        createKtorServer(config)
            .apply { start(wait = true) }
    }
}