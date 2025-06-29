package com.moshy.drugcalc.server.http.routing

import com.moshy.drugcalc.calc.calc.*
import com.moshy.drugcalc.calc.datacontroller.*
import calc.Evaluator
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
internal class CalcGetRoute(
    val noDecode: Boolean = false,
    /* ConfigMap params
     * TODO: use a build step to read the values for Config so they can be pasted here
     *       (with appropriate @SerialName injected annotations)
     */
        @SerialName("tick")
        @Contextual
        val tickDuration: Duration? = null,
        @SerialName("cutoff")
        val cutoffMg: Double? = null,
        @SerialName("lambdacorrection")
        val doLambdaCorrection: Boolean? = null,
    /* auxData params */
        val data: UrlString? = null,
    /* CycleDescription params */
        @SerialName(PARAM_FLAGS)
        val flags: UrlStringList? = null,
        @SerialName(PARAM_COMPOUND_OR_BLEND)
        val compoundOrBlendNames: UrlStringList? = null,
        @SerialName(PARAM_VARIANT_OR_TRANSFORMER)
        val variantOrTransformerNames: UrlStringList? = null,
        @SerialName(PARAM_DOSE)
        val doses: UrlStringList? = null,
        @SerialName(PARAM_START)
        val starts: UrlStringList? = null,
        @SerialName(PARAM_DURATION)
        val durations: UrlStringList? = null,
        @SerialName(PARAM_FREQ_NAME)
        val frequencyNames: UrlStringList? = null,
) {
    private fun validateQueryParametersForGet() {
        requireNotNull(compoundOrBlendNames) { "missing required param [$PARAM_COMPOUND_OR_BLEND]" }
        requireNotNull(doses) { "missing required param [$PARAM_DOSE]" }
        requireNotNull(starts) { "missing required param [$PARAM_START]" }
        requireNotNull(durations) { "missing required param [$PARAM_DURATION]" }
        requireNotNull(frequencyNames) { "missing required param [$PARAM_FREQ_NAME]" }

        val len1 = compoundOrBlendNames.value.size
        fun checkLen(value: UrlStringList, qName: String) =
            require(value.value.size == len1) { "wrong list-length for param [$qName]" }
        fun String.requireNonEmpty(atPos: Int, qName: String) =
            require(isNotEmpty()) { "unexpected empty value at position $atPos for param [$qName]" }

        flags?.let { checkLen(it, PARAM_FLAGS) }
        variantOrTransformerNames?.let { checkLen(it, PARAM_VARIANT_OR_TRANSFORMER) }
        checkLen(doses, PARAM_DOSE)
        checkLen(starts, PARAM_START)
        checkLen(durations, PARAM_DURATION)
        checkLen(frequencyNames, PARAM_FREQ_NAME)
        for (i in 0..<len1) {
            compoundOrBlendNames.value[i].requireNonEmpty(i, PARAM_COMPOUND_OR_BLEND)
            starts.value[i].requireNonEmpty(i, PARAM_START)
            durations.value[i].requireNonEmpty(i, PARAM_DURATION)
            frequencyNames.value[i].requireNonEmpty(i, PARAM_FREQ_NAME)
        }
    }

    fun buildCycleDescriptionFromQuery(serializersModule: SerializersModule): List<CycleDescription> =
        validateQueryParametersForGet().let {
            buildCycleDescriptionFromTokens(
                flags?.value,
                compoundOrBlendNames!!.value,
                variantOrTransformerNames?.value,
                doses!!.value,
                starts!!.value,
                durations!!.value,
                frequencyNames!!.value,
                serializersModule
            )
        }

    fun buildConfigMapFromQuery(): ProxyMap<Config>? =
        buildMap {
            tickDuration?.let { put("tickDuration", it ) }
            cutoffMg?.let { put("cutoffMilligrams", it) }
            (doLambdaCorrection ?: false).takeIf { it }
            ?.let { put("doLambdaDoseCorrection", it) }
        }.takeIf { it.isNotEmpty() }
        ?.let{ ProxyMap<_>(it) }

    // discouraged because the auxdata will have to be small due to all the escaping
    fun getAuxDataFromQuery(jsonModule: Json): Data? =
        data?.let { jsonModule.decodeFromString<Data>(it.value) }

    companion object {
        const val PARAM_FLAGS = "fl"
        const val PARAM_COMPOUND_OR_BLEND = "cb"
        const val PARAM_VARIANT_OR_TRANSFORMER = "vx"
        const val PARAM_DOSE = "d"
        const val PARAM_START = "s"
        const val PARAM_DURATION = "t"
        const val PARAM_FREQ_NAME = "fn"
    }

    @Resource("-test-c-")
    internal class CalcTestRoute(val parent: CalcGetRoute) {
        @Resource("decode")
        internal class Decode(val parent: CalcTestRoute)
    }

}

@Resource("calc")
internal class CalcPostRoute(
    @SerialName("nodecode")
    val noDecode: Boolean = false
)


internal fun Route.configureCalcRoutes(
    flags: AppConfig.DevFlags,
    controller: DataController,
    evaluator: Evaluator,
    jsonModule: Json
) {
    get<CalcGetRoute> { params ->
        doCalc(controller, evaluator,
            params.buildCycleDescriptionFromQuery(jsonModule.serializersModule),
            params.buildConfigMapFromQuery(),
            params.getAuxDataFromQuery(jsonModule),
            params.noDecode
        )
    }
    if (flags.enableTestonlyEndpoints) {
        get<CalcGetRoute.CalcTestRoute.Decode, CycleRequest> { params ->
            CycleRequest(
                params.parent.parent.getAuxDataFromQuery(jsonModule),
                params.parent.parent.buildConfigMapFromQuery(),
                params.parent.parent.buildCycleDescriptionFromQuery(jsonModule.serializersModule),
            )
        }
    }
    post<CalcPostRoute, CycleRequest> { params, req ->
        doCalc(
            controller, evaluator,
            req.cycle, req.config, req.data,
            params.noDecode
        )
    }

}

private suspend fun RoutingContext.doCalc(
    controller: DataController,
    evaluator: Evaluator,
    cycle: List<CycleDescription>,
    auxConfig: ProxyMap<Config>?,
    auxData: Data?,
    noDecode: Boolean
) {
    val resolved = controller.resolveNamesForCycle(cycle, auxData)
    val config = Config().run {
        auxConfig?.let { this + auxConfig } ?: this
    }
    val decoder = Decoder(config, resolved)
    val toCalc = decoder.decode(cycle)

    val result = evaluator.evaluateCycle(toCalc, config)
    if (noDecode)
        call.respond(result)
    else {
        val decodedResult = result.decodeTimeTickScaling(config)
        call.respond(decodedResult)
    }
}