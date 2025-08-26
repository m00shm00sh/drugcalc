package com.moshy.drugcalc.cmdclient

import com.moshy.drugcalc.cmdclient.handlers.*
import com.moshy.drugcalc.cmdclient.states.ForConnect
import com.moshy.drugcalc.cmdclient.util.AppConfig
import com.moshy.drugcalc.common.CacheEvictionPolicy
import com.moshy.drugcalc.types.login.UserRequest
import com.moshy.krepl.Repl

suspend fun main(args: Array<String>) {
    val config = AppConfig.config(args)

    val repl = Repl()
    val cacheParams = CacheEvictionPolicy()
    val state = AppState(cacheParams).apply {
        with(config) {
            forConnect.connect = conn
            forLogin.login = login
            pageSize?.let {
                this@apply.pageSize = it
            }
        }
    }
    repl["connect"](state.configureConnect())
    repl["login"](state.configureLogin())
    repl["data"](state.configureData())
    repl["calc"](state.configureCalc())
    repl.run()
}
