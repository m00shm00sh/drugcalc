package com.moshy.drugcalc.cmdclient.io

import com.moshy.drugcalc.common.toTruthy
import com.moshy.drugcalc.types.dataentry.BlendName
import com.moshy.drugcalc.types.dataentry.CompoundBase
import com.moshy.drugcalc.types.dataentry.FrequencyName
import com.moshy.krepl.InputReceiveChannel
import com.moshy.krepl.OutputSendChannel
import com.moshy.krepl.asNonLine

internal suspend fun confirmOperation(what: String, in_: InputReceiveChannel, out: OutputSendChannel) {
    out.send("confirm $what:".asNonLine())
    val resp = in_.receive()
    require(resp.toTruthy()) {
        "confirm failed"
    }
}

internal fun quote(s: String) =
    when (s.indexOf(' ')) {
        -1 -> s
        else -> "\"" + s.replace("\"", "\\\"") + "\""
    }

internal fun quote(s: CompoundBase) = quote(s.value)
internal fun quote(s: BlendName) = quote(s.value)
internal fun quote(s: FrequencyName) = quote(s.value)

