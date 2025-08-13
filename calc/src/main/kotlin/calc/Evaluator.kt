package com.moshy.drugcalc.calc.calc

import com.moshy.drugcalc.common.CacheEvictionPolicy
import com.moshy.drugcalc.common.BoundThreadpoolDispatcher
import com.moshy.drugcalc.types.calccommand.CycleResult
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** Executor for evaluating cycle inside thread pool. */
class Evaluator(
    policy: Config,
    private val dispatcher: CoroutineContext = BoundThreadpoolDispatcher(policy.nThreads) + SupervisorJob()
) {
    // TODO: consider cache based on hash of pair(cycle, config)
    suspend fun evaluateCycle(cycle: CycleCalculation, config: com.moshy.drugcalc.types.calccommand.Config): CycleResult =
        withContext(dispatcher) {
            evaluateDecodedCycle(cycle, config)
        }

    data class Config(
        val nThreads: Int = Runtime.getRuntime().availableProcessors(),
        val cacheEvictionPolicy: CacheEvictionPolicy = CacheEvictionPolicy(),
    )
}