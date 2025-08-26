package com.moshy.drugcalc.cmdclient.handlers

import com.moshy.drugcalc.cmdclient.AppState
import com.moshy.drugcalc.cmdclient.states.ForConnect
import com.moshy.drugcalc.common.toTruthy
import com.moshy.krepl.Repl
import com.moshy.krepl.asLine


internal fun AppState.configureConnect(): Repl.EntryBuilder.() -> Unit = {
    val app = this@configureConnect
    usage = "connect proto://host:port {insec=truthy}?"
    help = "configure and/or initialize connection"
    handler = { (pos, kw, _, out) ->
        val insec = kw["insec"].toTruthy()

        with(app.forConnect) {
            val addr = pos.firstOrNull()
            when (pos.size) {
                1 -> connect = ForConnect.ConnectParams(addr!!, insec)
                0 -> requireNotNull(connect) {
                    "empty saved connection config"
                }

                else -> throw IllegalArgumentException("unexpected arg count: ${pos.size}")
            }
            getClient()
        }
        out.send("connect: OK".asLine())
    }
}

