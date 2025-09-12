package com.moshy.drugcalc.cmdclient.states

import com.moshy.drugcalc.types.calccommand.*
import com.moshy.ProxyMap
import com.moshy.drugcalc.cmdclient.AppState
import com.moshy.drugcalc.common.logger
import kotlinx.serialization.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@Serializable
internal class ForCalc {
    @Transient
    val logger = logger("${AppState.NAME}:calc")

    var config: ProxyMap<Config> = ProxyMap()
    var reqCycles: List<CycleDescription> = emptyList()

    @Transient
    var calcResult: CycleResult = emptyMap()
    var lineDuration: Duration = 1.days
    var calcTimeTick: Duration = DEFAULT_CONFIG["tickDuration"] as Duration

    companion object {
        val DEFAULT_CONFIG = ProxyMap<Config>("tickDuration" to 6.hours)
    }
}