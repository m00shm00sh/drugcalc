package com.moshy.drugcalc.server

import com.moshy.drugcalc.server.http.createKtorServer
import com.moshy.drugcalc.server.util.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    val config = AppConfig.config(args)
    // we need IO dispatcher to prevent deadlock arising from request -> cache miss -> fetch -> db io
    runBlocking(Dispatchers.IO) {
        createKtorServer(config)
            .apply { start(wait = true) }
    }
}