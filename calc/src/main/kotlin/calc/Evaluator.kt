package com.moshy.drugcalc.calc.calc

import com.moshy.drugcalc.common.CacheEvictionPolicy
import com.moshy.drugcalc.common.BoundThreadpoolDispatcher
import com.moshy.drugcalc.types.calccommand.Config as CalcConfig
import com.moshy.drugcalc.types.calccommand.CycleResult
import com.moshy.drugcalc.types.calccommand.XYList
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** Executor for evaluating cycle inside thread pool. */
class Evaluator(
    policy: Config,
    private val dispatcher: CoroutineContext = BoundThreadpoolDispatcher(policy.nThreads) + SupervisorJob()
) {
    // TODO: consider cache based on hash of pair(cycle, config)
    // TODO: cacheable decoding
    suspend fun evaluateCycle(cycles: Map<Int, CycleCalculation>, config: CalcConfig)
    : CycleResult<XYList.OfRaw> =
        withContext(dispatcher) {
            cycles.entries
                .map { (group, calculation) -> group to evaluateDecodedCycle(calculation, config) }
                .flatMap { (group, evaluated) ->
                    evaluated.entries.map { (compound, values) ->
                        groupAwareCompoundName(group, compound) to values
                    }
                }
                .toMap()
            .let { CycleResult(it) }
        }

    suspend fun evaluateCycle(cycle: CycleCalculation, config: CalcConfig)
        = evaluateCycle(mapOf(0 to cycle), config)

    private fun groupAwareCompoundName(group: Int, compound: String) =
        if (group > 0) "$group:$compound"
        else compound

    data class Config(
        val nThreads: Int = Runtime.getRuntime().availableProcessors(),
        val cacheEvictionPolicy: CacheEvictionPolicy = CacheEvictionPolicy(),
    )
}