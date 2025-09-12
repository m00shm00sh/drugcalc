package com.moshy.drugcalc.cmdclient

import com.moshy.drugcalc.common.*
import com.moshy.drugcalc.cmdclient.states.ForCalc
import com.moshy.drugcalc.cmdclient.states.ForConnect
import com.moshy.drugcalc.cmdclient.states.ForData
import com.moshy.drugcalc.cmdclient.states.ForLogin
import com.moshy.krepl.OutputSendChannel
import com.moshy.krepl.Repl
import com.moshy.krepl.asLine
import kotlinx.serialization.*
import java.lang.ref.WeakReference

@Serializable
internal class AppState {
    @Transient
    val logger = logger(NAME)

    val forConnect = ForConnect()

    val forLogin = ForLogin()

    val forData = ForData()

    val forCalc = ForCalc()

    var pageSize: Int = 0

    internal suspend fun maybePaginate(repl: Repl, lines: List<String>, out: OutputSendChannel) {
        logger.debug("paginate {} lines with {} pageSize", lines.size, pageSize)
        if (pageSize > 0 && lines.size > pageSize)
            repl.paginate(lines, out, pageSize)
        else
            for (line in lines) {
                out.send("\t$line".asLine())
            }
    }

    fun fromConfig() =
        this.apply {
            forData.app = WeakReference(this)
        }

    companion object {
        const val NAME = "CliClient"
    }
}