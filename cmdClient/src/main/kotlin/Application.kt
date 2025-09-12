package com.moshy.drugcalc.cmdclient

import com.moshy.drugcalc.cmdclient.handlers.*
import com.moshy.krepl.Repl
import kotlinx.serialization.json.Json
import java.io.File

suspend fun main() {
    val configFile = File("dc-client.json")
    val state =
        when {
            configFile.exists() ->
                Json.decodeFromString<AppState>(configFile.readText())

            else ->
                AppState()
        }.fromConfig()


    val repl = Repl()
    repl["connect"](state.configureConnect())
    repl["login"](state.configureLogin())
    repl["data"](state.configureData())
    repl["calc"](state.configureCalc())
    repl.run()

    val prettyJson = Json { prettyPrint = true }
    prettyJson.encodeToString(state).let {
        configFile.writeText(it)
    }
}
