package com.moshy.drugcalc.server.http.routing

import com.moshy.drugcalc.calc.calc.*
import com.moshy.drugcalc.calc.datacontroller.*
import com.moshy.drugcalc.types.calccommand.*
import com.moshy.drugcalc.server.http.routing.util.*
import com.moshy.drugcalc.server.util.AppConfig
import com.moshy.proxymap.plus

import io.ktor.resources.Resource
import io.ktor.server.routing.Route

@Resource("calc")
internal class CalcPostRoute

internal fun Route.configureCalcRoutes(
    flags: AppConfig.DevFlags,
    controller: DataController,
    evaluator: Evaluator,
) {
    post<CalcPostRoute, CycleRequest, CycleResult<XYList>> { _, req ->
        val resolved = controller.resolveNamesForCycle(req.cycle, req.data)
        val config = Config().run {
            req.config?.let { this + it } ?: this
        }
        val decoder = Decoder(config, resolved)
        val toCalc = decoder.decode(req.cycle)

        val result = evaluator.evaluateCycle(toCalc, config)

        @Suppress("UNCHECKED_CAST") // upcast to force polymorphic-base serialization
        req.decode.decodeGivenConfig(result, config) as CycleResult<XYList>
    }
}