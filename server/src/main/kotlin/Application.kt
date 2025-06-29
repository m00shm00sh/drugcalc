package com.moshy.drugcalc.server

import com.moshy.drugcalc.server.http.createKtorServer
import com.moshy.drugcalc.server.util.AppConfig
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    val config = AppConfig.config(args)
    runBlocking {
        createKtorServer(config)
            .apply { start(wait = true) }
    }
}