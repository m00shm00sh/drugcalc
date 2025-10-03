package com.moshy.drugcalc.server.http.routing

import com.moshy.drugcalc.calc.calc.*
import com.moshy.drugcalc.calc.datacontroller.*
import com.moshy.drugcalc.types.calccommand.*
import com.moshy.drugcalc.types.dataentry.*
import com.moshy.drugcalc.server.http.routing.util.*
import com.moshy.ProxyMap
import com.moshy.drugcalc.server.util.AppConfig
import com.moshy.proxymap.plus

import io.ktor.resources.Resource
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.time.Duration

@Resource("calc")
internal class CalcPostRoute(
    val noDecode: Boolean = false
)


internal fun Route.configureCalcRoutes(
    flags: AppConfig.DevFlags,
    controller: DataController,
    evaluator: Evaluator,
) {
    post<CalcPostRoute, CycleRequest, CycleResult<XYList>> { params, req ->
        val resolved = controller.resolveNamesForCycle(req.cycle, req.data)
        val config = Config().run {
            req.config?.let { this + it } ?: this
        }
        val decoder = Decoder(config, resolved)
        val toCalc = decoder.decode(req.cycle)

        val result = evaluator.evaluateCycle(toCalc, config)

        val resultDecoder =
            if (req.decode !is DecodeSpec.ToDuration)
                req.decode
            else when (params.noDecode) {
                false -> DecodeSpec.ToDuration()
                true -> DecodeSpec.None()
            }
        resultDecoder.decodeGivenConfig(result, config) as CycleResult<XYList>
    }
}