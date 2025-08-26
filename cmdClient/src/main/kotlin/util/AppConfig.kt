package com.moshy.drugcalc.cmdclient.util

import com.moshy.drugcalc.cmdclient.states.ForConnect
import com.moshy.drugcalc.types.login.UserRequest
import com.sksamuel.hoplite.*
import com.sksamuel.hoplite.fp.getOrElse

internal object AppConfig {

    data class App(
        val conn: ForConnect.ConnectParams? = null,
        val login: UserRequest? = null,
        val user: String? = null,
        val pass: String? = null,
        val pageSize: Int? = null,
    ) {
        init {
            pageSize?.let {
                require(it >= 0) {
                    "invalid pageSize"
                }
            }
        }
    }

    fun config(args: Array<String>) =
        ConfigLoaderBuilder.default()
            .addCommandLineSource(args, prefix = "")
            .addResourceSource("/application.conf")
            .addFileSource("application.conf", optional = true)
            .build()
            .loadConfig<App>()
            .getOrElse {
                if (it == ConfigFailure.UndefinedTree)
                    App()
                else
                    throw IllegalArgumentException("Error loading config: $it")
            }
}