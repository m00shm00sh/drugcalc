package com.moshy.drugcalc.cmdclient.states

import com.moshy.drugcalc.types.calccommand.*
import com.moshy.ProxyMap
import com.moshy.drugcalc.cmdclient.AppState
import com.moshy.drugcalc.common.logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

internal class ForCalc {
    val logger = logger("${AppState.NAME}:calc")

    var config: ProxyMap<Config> = ProxyMap()
    var reqCycles: List<CycleDescription> = emptyList()
    var calcResult: DecodedCycleResult = emptyMap()
    var lineDuration: Duration = 1.days

    companion object {
        val DEFAULT_CONFIG = ProxyMap<Config>("tickDuration" to 6.hours)
    }
}