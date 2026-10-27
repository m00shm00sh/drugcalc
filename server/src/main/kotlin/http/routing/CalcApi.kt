package com.moshy.drugcalc.server.http.routing

import kotlinx.coroutines.*
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

        // flatten map of group -> calculation into map of "group:compound" -> values
        val result: CycleResult<XYList.OfRaw> = coroutineScope {
            toCalc.entries
                .map { (group, groupCalculation) ->
                    async {
                        group to evaluator.evaluateCycle(groupCalculation, config)
                    }
                }
                .awaitAll()
                .flatMap { (group, groupResult) ->
                    groupResult.entries.map { (compound, values) ->
                        groupAwareCompoundName(group, compound) to values
                    }
                }
                .toMap()
        }.let { CycleResult(it) }

        @Suppress("UNCHECKED_CAST") // upcast to force polymorphic-base serialization
        req.decode.decodeGivenConfig(result, config) as CycleResult<XYList>
    }
}

private fun groupAwareCompoundName(group: Int, compound: String) =
    if (group > 0) "$group:$compound"
    else compound