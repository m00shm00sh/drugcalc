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
    suspend fun evaluateCycle(cycle: CycleCalculation, config: CalcConfig)
    : CycleResult<XYList.OfRaw> =
        withContext(dispatcher) {
            evaluateDecodedCycle(cycle, config)
        }

    data class Config(
        val nThreads: Int = Runtime.getRuntime().availableProcessors(),
        val cacheEvictionPolicy: CacheEvictionPolicy = CacheEvictionPolicy(),
    )
}